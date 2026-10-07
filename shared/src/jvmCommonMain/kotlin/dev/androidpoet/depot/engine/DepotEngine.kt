package dev.androidpoet.depot.engine

import dev.androidpoet.depot.catalog.AppVersion
import dev.androidpoet.depot.catalog.Catalog
import dev.androidpoet.depot.catalog.CatalogApp
import dev.androidpoet.depot.repo.ApkInspector
import dev.androidpoet.depot.repo.CatalogCache
import dev.androidpoet.depot.repo.EntryVerificationException
import dev.androidpoet.depot.repo.EntryVerifier
import dev.androidpoet.depot.repo.FDROID_FINGERPRINT
import dev.androidpoet.depot.repo.FDROID_REPO
import dev.androidpoet.depot.repo.IndexReader
import dev.androidpoet.depot.repo.IntegrityException
import dev.androidpoet.depot.repo.RepoClient
import dev.androidpoet.depot.repo.SyncDecision
import dev.androidpoet.depot.repo.apkFileName
import dev.androidpoet.depot.repo.repoUrl
import dev.androidpoet.depot.repo.requireExpectedApk
import dev.androidpoet.depot.repo.requireIntegrity
import dev.androidpoet.depot.repo.syncDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class DepotEngine(
    dataDir: File,
    private val cacheDir: File,
    private val target: TargetDevice,
    private val repo: RepoClient = RepoClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = System::currentTimeMillis,
) : Depot {
    private val cache = CatalogCache(dataDir)
    private val installJobs = ConcurrentHashMap<String, Job>()
    private var syncJob: Job? = null

    private val _catalog = MutableStateFlow<Catalog?>(null)
    private val _lastChecked = MutableStateFlow<Long?>(null)
    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    private val _device = MutableStateFlow<DeviceState>(DeviceState.Unknown)
    private val _installs = MutableStateFlow<Map<String, InstallState>>(emptyMap())

    override val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()
    override val lastChecked: StateFlow<Long?> = _lastChecked.asStateFlow()
    override val sync: StateFlow<SyncState> = _sync.asStateFlow()
    override val device: StateFlow<DeviceState> = _device.asStateFlow()
    override val installs: StateFlow<Map<String, InstallState>> = _installs.asStateFlow()

    override fun start() {
        refreshDevice()
        scope.launch {
            withContext(Dispatchers.IO) {
                _catalog.value = cache.load()
                _lastChecked.value = cache.lastChecked()
            }
            val checked = _lastChecked.value
            if (_catalog.value == null || checked == null || now() - checked > STALE_AFTER_MILLIS) refresh()
        }
    }

    @Synchronized
    override fun refresh() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            _sync.value = SyncState.Checking
            _sync.value = try {
                withContext(Dispatchers.IO) { sync() }
                SyncState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncState.Failed(describe(e))
            }
        }
    }

    override fun refreshDevice() {
        scope.launch { _device.value = withContext(Dispatchers.IO) { target.probe() } }
    }

    override fun install(app: CatalogApp, version: AppVersion) {
        val packageName = app.packageName
        var claimed = false
        _installs.update { current ->
            claimed = current[packageName]?.busy != true
            if (claimed) current + (packageName to InstallState.Downloading(0f)) else current
        }
        if (!claimed) return
        installJobs[packageName] = scope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) { downloadAndInstall(packageName, version) }
            } catch (e: CancellationException) {
                setInstall(packageName, null)
                throw e
            } catch (e: Exception) {
                InstallState.Failed(describe(e))
            }
            setInstall(packageName, outcome)
            if (outcome == InstallState.Done) refreshDevice()
        }
    }

    override fun cancelInstall(packageName: String) {
        val job = installJobs.remove(packageName)
        if (job?.isActive == true) job.cancel() else setInstall(packageName, null)
    }

    private suspend fun sync() {
        val entry = EntryVerifier.verify(repo.fetchBytes("$FDROID_REPO/entry.jar", EntryVerifier.MAX_JAR_BYTES), FDROID_FINGERPRINT)
        when (syncDecision(_catalog.value?.timestamp, entry.timestamp)) {
            SyncDecision.Rollback -> throw IntegrityException(
                "The repository offered a catalogue older than the one already stored (${entry.timestamp} < ${_catalog.value?.timestamp})",
            )
            SyncDecision.UpToDate -> Unit
            SyncDecision.Download -> {
                val url = repoUrl(entry.indexName) ?: throw IntegrityException("The entry names an index outside the repository: ${entry.indexName}")
                val part = File(cacheDir, "index-v2.json.part")
                try {
                    repo.download(url, part, entry.indexSize) { _sync.value = SyncState.Downloading(it, entry.indexSize) }
                    requireIntegrity(part, entry.indexSha256, entry.indexSize)
                    _sync.value = SyncState.Reading
                    val fresh = part.inputStream().buffered().use(IndexReader::read).copy(timestamp = entry.timestamp)
                    cache.save(fresh)
                    _catalog.value = fresh
                } finally {
                    part.delete()
                }
            }
        }
        val checkedAt = now()
        cache.markChecked(checkedAt)
        _lastChecked.value = checkedAt
    }

    private suspend fun downloadAndInstall(packageName: String, version: AppVersion): InstallState {
        val url = repoUrl(version.apkPath) ?: return InstallState.Failed("The catalogue names an APK outside the repository")
        val apk = File(File(cacheDir, "apks"), apkFileName(version.sha256))
        try {
            repo.download(url, apk, version.size) { bytes ->
                setInstall(packageName, InstallState.Downloading(bytes.toFloat() / version.size))
            }
            setInstall(packageName, InstallState.Verifying)
            try {
                requireIntegrity(apk, version.sha256, version.size)
            } catch (e: IntegrityException) {
                return InstallState.Failed("Checksum did not match, the download was discarded")
            }
            try {
                requireExpectedApk(ApkInspector.inspect(apk), packageName, version)
            } catch (e: IntegrityException) {
                return InstallState.Failed("${e.message}. The download was discarded")
            }
            setInstall(packageName, InstallState.Installing)
            val failure = target.install(apk, packageName) { setInstall(packageName, InstallState.AwaitingConfirmation) }
            return if (failure == null) InstallState.Done else InstallState.Failed(failure)
        } finally {
            apk.delete()
        }
    }

    private fun setInstall(packageName: String, state: InstallState?) {
        _installs.update { if (state == null) it - packageName else it + (packageName to state) }
    }

    private fun describe(error: Exception): String = when (error) {
        is UnknownHostException -> "No connection to f-droid.org"
        is EntryVerificationException, is IntegrityException -> error.message.orEmpty()
        is IOException -> "The connection to f-droid.org was interrupted (${error.message}). Try again"
        else -> "${error::class.simpleName}: ${error.message}"
    }

    private companion object {
        const val STALE_AFTER_MILLIS = 12 * 60 * 60 * 1000L
    }
}
