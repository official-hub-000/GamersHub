cmake_minimum_required(VERSION 3.22)
project(dfroot)
enable_language(ASM)

set(JNI_DIR ${CMAKE_CURRENT_SOURCE_DIR})

set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -I${JNI_DIR}")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${C_FLAGS} -I${JNI_DIR} -Wall -Wextra -fno-stack-protector -fomit-frame-pointer")

set(SPLICEHELPER "${JNI_DIR}/splicehelper")
add_custom_command(
    OUTPUT "${SPLICEHELPER}"
    COMMAND "${CMAKE_C_COMPILER}"
            --target=${CMAKE_C_COMPILER_TARGET}
            --sysroot=${CMAKE_SYSROOT}
            "${JNI_DIR}/splicehelper.c"
            -o "${SPLICEHELPER}"
            -nodefaultlibs -nostartfiles -ffreestanding -static
    COMMAND "${CMAKE_STRIP}" "${SPLICEHELPER}"
    DEPENDS "${JNI_DIR}/splicehelper.c"
    COMMENT "Building splicehelper"
)
add_custom_target(splicehelper_bin DEPENDS "${SPLICEHELPER}")

set(KO_FILES
    "${JNI_DIR}/ko/dfroot-android12-5.10.ko"
    "${JNI_DIR}/ko/dfroot-android13-5.10.ko"
    "${JNI_DIR}/ko/dfroot-android13-5.15.ko"
    "${JNI_DIR}/ko/dfroot-android14-5.15.ko"
    "${JNI_DIR}/ko/dfroot-android14-6.1.ko"
    "${JNI_DIR}/ko/dfroot-android15-6.6.ko"
    "${JNI_DIR}/ko/dfroot-android16-6.12.ko"
    "${JNI_DIR}/ko/dfroot-android17-6.18.ko"
)

# AGP only packages lib*.so, so the executable is named libdfroot.so and lands
# in the APK's native lib dir. The app execs it from nativeLibraryDir, which is
# executable (app data dir is not).
add_executable(dfroot exp.c libcxx.S elf_parser.c)
add_dependencies(dfroot splicehelper_bin)
set_target_properties(dfroot PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

set_source_files_properties(exp.c PROPERTIES
    OBJECT_DEPENDS "${KO_FILES};${SPLICEHELPER}")

target_link_libraries(dfroot log)
target_link_options(dfroot PRIVATE -Wl,-z,max-page-size=16384)

add_executable(bootstrap bootstrap.c)
set_target_properties(bootstrap PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
target_link_options(bootstrap PRIVATE -Wl,-z,max-page-size=16384)

# The payload and the app have to agree on where the staged executables live, and three
# of the four parties compute it in different languages. The native side no longer
# guesses: it writes its compiled path into this file at build time, the app reads it and
# compares. A mismatch is refused up front instead of loading the module, going permissive
# and exec'ing a path that does not exist.
#
# It is an ELF stub rather than a text file because AGP only packages lib*.so out of
# nativeLibraryDir; the path itself is appended as a trailer and read back by offset.
set(PAYLOAD_DIR "/data/user_de/0/com.gamershub")
add_custom_command(
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so"
    COMMAND "${CMAKE_C_COMPILER}" -x c -c -o "${CMAKE_CURRENT_BINARY_DIR}/pathmarker.o" "${JNI_DIR}/pathmarker.c"
    COMMAND "${CMAKE_C_COMPILER}" -shared -o "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so" "${CMAKE_CURRENT_BINARY_DIR}/pathmarker.o" -nostdlib
    COMMAND "${CMAKE_COMMAND}" -E echo "${PAYLOAD_DIR}/bootstrap" > "${CMAKE_CURRENT_BINARY_DIR}/marker.tmp"
    COMMAND cat "${CMAKE_CURRENT_BINARY_DIR}/marker.tmp" >> "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so"
    DEPENDS "${JNI_DIR}/pathmarker.c"
    COMMENT "Writing payload layout marker (${PAYLOAD_DIR})"
)
add_custom_target(pathmarker DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so")
add_dependencies(dfroot pathmarker)
add_dependencies(bootstrap pathmarker)

# AGP collects lib*.so from the CMake library output dir, so copy the marker there.
add_custom_command(TARGET pathmarker POST_BUILD
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different
            "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so"
            "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libdfroot-path.so")
