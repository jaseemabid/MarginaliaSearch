package nu.marginalia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class NativeAlgos {
    private final MethodHandle qsortHandle;
    private final MethodHandle qsort128Handle;
    private final MethodHandle linearSearch64Handle;
    private final MethodHandle linearSearch128Handle;
    private final MethodHandle binarySearch128Handle;
    private final MethodHandle binarySearch64UpperHandle;

    public static final NativeAlgos instance;
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(NativeAlgos.class);

    private NativeAlgos(Path libFile) throws Exception {
        var libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();

        var handle = libraryLookup.find("ms_sort_64").get();
        qsortHandle = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_sort_128").get();
        qsort128Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_linear_search_64").get();
        linearSearch64Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_linear_search_128").get();
        linearSearch128Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_binary_search_128").get();
        binarySearch128Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_binary_search_64upper").get();
        binarySearch64UpperHandle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG));
    }

    static {
        Path libFile;
        NativeAlgos nativeAlgosI = null;
        // copy resource to temp file
        try (var is = NativeAlgos.class.getClassLoader().getResourceAsStream("libcpp.so")) {
            var tempFile = File.createTempFile("libcpp", ".so");
            tempFile.deleteOnExit();

            try (var os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
            }

            libFile = tempFile.toPath();
            nativeAlgosI = new NativeAlgos(libFile);
        }
        catch (Exception e) {

        }

        instance = nativeAlgosI;
        isAvailable = instance != null;
    }


    public static void sort(MemorySegment ms, long start, long end) {
        try {
            instance.qsortHandle.invoke(ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void sort128(MemorySegment ms, long start, long end) {
        try {
            instance.qsort128Handle.invoke(ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long linearSearch64(long key, MemorySegment ms, long start, long end) {
        try {
            return (long) instance.linearSearch64Handle.invoke(key, ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long linearSearch128(long key, MemorySegment ms, long start, long end) {
        try {
            return (long) instance.linearSearch128Handle.invoke(key, ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long binarySearch128(long key, MemorySegment ms, long start, long end) {
        try {
            return (long) instance.binarySearch128Handle.invoke(key, ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long binarySearch64Upper(long key, MemorySegment ms, long start, long end) {
        try {
            return (long) instance.binarySearch64UpperHandle.invoke(key, ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }
}
