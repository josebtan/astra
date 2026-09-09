package com.astra.core.storage

import java.io.File

/**
 * Enforces the "los datos son sagrados" principle from the roadmap
 * (section 4): every session gets its own RAW/, WORK/ and RESULTS/
 * directories, and this is the *only* place in the codebase that should
 * construct paths into them.
 *
 * ```
 * <storageRoot>/<sessionName>/RAW/...        never written to after capture
 * <storageRoot>/<sessionName>/WORK/...       intermediate, disposable
 * <storageRoot>/<sessionName>/RESULTS/...    final outputs
 * ```
 */
class SessionFileStore(storageRoot: File, sessionName: String) {

    val sessionDir: File = File(storageRoot, sessionName)
    val rawDir: File = File(sessionDir, "RAW")
    val workDir: File = File(sessionDir, "WORK")
    val resultsDir: File = File(sessionDir, "RESULTS")

    init {
        listOf(rawDir, workDir, resultsDir).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                error("Could not create directory: ${dir.absolutePath}")
            }
        }
    }

    /** Path for a newly captured RAW frame. Never call this twice for the same file name. */
    fun newRawFile(fileName: String): File {
        val target = File(rawDir, fileName)
        check(!target.exists()) { "Refusing to overwrite existing RAW file: ${target.absolutePath}" }
        return target
    }

    /** Path for an intermediate/derived file, e.g. WORK/calibrated/IMG_000001.tif */
    fun newWorkFile(subfolder: String, fileName: String): File {
        val dir = File(workDir, subfolder)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    /** Path for a final deliverable, e.g. RESULTS/stacked.fits */
    fun newResultFile(fileName: String): File = File(resultsDir, fileName)
}
