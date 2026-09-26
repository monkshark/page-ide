package page.lsp

import com.sun.jna.IntegerType
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class WindowsProcessJob private constructor(private val handle: Pointer) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) kernel.CloseHandle(handle)
    }

    private fun assign(pid: Long) {
        val process = kernel.OpenProcess(PROCESS_SET_QUOTA or PROCESS_TERMINATE or PROCESS_QUERY_INFORMATION, false, pid.toInt())
            ?: throw failure("OpenProcess")
        try {
            val assigned = IntByReference()
            if (kernel.IsProcessInJob(process, handle, assigned) && assigned.value != 0) return
            if (!kernel.AssignProcessToJobObject(handle, process)) throw failure("AssignProcessToJobObject")
        } finally {
            kernel.CloseHandle(process)
        }
    }

    class SizeT : IntegerType(Native.SIZE_T_SIZE, 0, true) {
        override fun toByte(): Byte = toLong().toByte()
        override fun toShort(): Short = toLong().toShort()
    }

    @Structure.FieldOrder(
        "processTime", "jobTime", "flags", "minWorkingSet", "maxWorkingSet", "activeProcesses",
        "affinity", "priority", "scheduling", "ioCounters", "processMemory", "jobMemory",
        "peakProcessMemory", "peakJobMemory",
    )
    class Limits : Structure() {
        @JvmField var processTime = 0L
        @JvmField var jobTime = 0L
        @JvmField var flags = 0x00002000
        @JvmField var minWorkingSet = SizeT()
        @JvmField var maxWorkingSet = SizeT()
        @JvmField var activeProcesses = 0
        @JvmField var affinity = SizeT()
        @JvmField var priority = 0
        @JvmField var scheduling = 0
        @JvmField var ioCounters = LongArray(6)
        @JvmField var processMemory = SizeT()
        @JvmField var jobMemory = SizeT()
        @JvmField var peakProcessMemory = SizeT()
        @JvmField var peakJobMemory = SizeT()
    }

    private interface Kernel : StdCallLibrary {
        fun CreateJobObjectW(attributes: Pointer?, name: Pointer?): Pointer?
        fun SetInformationJobObject(job: Pointer, type: Int, limits: Limits, size: Int): Boolean
        fun AssignProcessToJobObject(job: Pointer, process: Pointer): Boolean
        fun IsProcessInJob(process: Pointer, job: Pointer, result: IntByReference): Boolean
        fun OpenProcess(access: Int, inherit: Boolean, pid: Int): Pointer?
        fun CloseHandle(handle: Pointer): Boolean
    }

    companion object {
        private const val PROCESS_TERMINATE = 0x0001
        private const val PROCESS_SET_QUOTA = 0x0100
        private const val PROCESS_QUERY_INFORMATION = 0x0400
        private const val EXTENDED_LIMIT_INFORMATION = 9
        private val kernel: Kernel by lazy { Native.load("kernel32", Kernel::class.java) }

        fun attach(process: Process): WindowsProcessJob? {
            if (!Platform.isWindows() || !process.isAlive) return null
            val handle = kernel.CreateJobObjectW(null, null) ?: throw failure("CreateJobObject")
            val job = WindowsProcessJob(handle)
            try {
                val limits = Limits()
                if (!kernel.SetInformationJobObject(handle, EXTENDED_LIMIT_INFORMATION, limits, limits.size())) {
                    throw failure("SetInformationJobObject")
                }
                job.assign(process.pid())
                process.descendants().use { descendants ->
                    descendants.forEach { child ->
                        try {
                            job.assign(child.pid())
                        } catch (error: IOException) {
                            if (child.isAlive) throw error
                        }
                    }
                }
                return job
            } catch (error: Throwable) {
                job.close()
                throw error
            }
        }

        private fun failure(operation: String) = IOException("$operation failed: Windows error ${Native.getLastError()}")
    }
}
