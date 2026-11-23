package com.civileg.app.data.repository

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

/** Isolated candidate, no DI binding. Private no-backup recovery, NOT encryption or cloud backup. */
class AndroidCostBoqAtomicStorage(context:Context):CostBoqAtomicStorage {
    private val directory=File(context.noBackupFilesDir,"cost-boq-drafts-v1")
    private fun file(projectId:Long):AtomicFile {
        require(projectId>0)
        check(directory.exists() || directory.mkdirs())
        check(directory.isDirectory)
        val target=File(directory,"project-$projectId.checkpoint")
        check(target.canonicalFile.parentFile==directory.canonicalFile)
        return AtomicFile(target)
    }
    @Synchronized override fun read(projectId:Long,maxBytes:Int):ByteArray? {
        val atomic=file(projectId)
        // AtomicFile.exists also recognizes its recovery backup. Corruption is never treated as absence.
        if(!atomic.exists()) return null
        return atomic.openRead().use { input ->
            val bytes=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) { val n=input.read(buffer);if(n<0)break
                require(bytes.size()+n<=maxBytes) { "Checkpoint exceeds recovery limit" }
                bytes.write(buffer,0,n)
            }
            bytes.toByteArray()
        }
    }
    @Synchronized override fun replace(projectId:Long,bytes:ByteArray) {
        require(bytes.size<=BoundedCostBoqCheckpointStore.MAX_BYTES)
        val atomic=file(projectId)
        var output:FileOutputStream?=null
        try { output=atomic.startWrite();output.write(bytes);atomic.finishWrite(output) }
        catch(failure:Throwable) { output?.let(atomic::failWrite);throw failure }
    }
    @Synchronized override fun delete(projectId:Long) { file(projectId).delete() }
}
