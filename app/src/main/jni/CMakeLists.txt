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

# The payload and the app have to agree on where the staged executables live, and three of
# the four parties compute it in different languages. The native side writes its compiled
# path into this marker at build time; the app reads it back and compares.
#
# Three earlier attempts each shipped a USELESS marker, and a marker without the path makes
# layoutMatches() false, which makes DFRoot refuse to run on every device:
#
#  1. `cmake -E echo ... > f` plus `>>` inside add_custom_command. CMake runs commands
#     without a shell, so both redirects became literal arguments and nothing was written.
#  2. Appending with `cmake -E cat ... >> f` — `-E cat` has no append mode, so the same
#     thing happened again.
#  3. A compiled generator target — but this is a cross-compile for arm64, so the tool
#     cannot execute on the x86 build host.
#
# So the marker is written by `file(WRITE)` at configure time. That needs no shell, no
# host-executable, and no append: the whole file is one write. It is plain text, not a
# valid ELF, and that is fine — AGP packages any lib*.so from the library output directory,
# which the previously-empty marker proved by being packaged successfully.
set(PAYLOAD_DIR "/data/user_de/0/com.gamershub")
set(MARKER_OUT "${CMAKE_CURRENT_BINARY_DIR}/libdfroot-path.so")
file(WRITE "${MARKER_OUT}" "${PAYLOAD_DIR}/bootstrap\n")

add_custom_command(
    OUTPUT "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libdfroot-path.so"
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different
            "${MARKER_OUT}" "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libdfroot-path.so"
    DEPENDS "${MARKER_OUT}"
    COMMENT "Installing payload layout marker (${PAYLOAD_DIR})"
    VERBATIM
)
add_custom_target(pathmarker ALL
    DEPENDS "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libdfroot-path.so")
add_dependencies(dfroot pathmarker)
add_dependencies(bootstrap pathmarker)
