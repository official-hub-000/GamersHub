/*
 * Placeholder object for the payload layout marker.
 *
 * The marker must be a lib*.so to be packaged, but it holds no code: CMake appends the
 * compiled payload directory as a trailer after this stub, and DfPaths reads it back.
 * Keeping it a real (if empty) shared object means the file still loads if anything ever
 * dlopen()s it by mistake.
 */
void gh_path_marker(void) {}
