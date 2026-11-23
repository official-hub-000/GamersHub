/*
 * Emits the payload layout marker: a tiny ELF shared object whose tail carries the exact
 * absolute path the kernel module will exec.
 *
 * ## Why a build-time program and not a CMake command chain
 *
 * The first attempt strung `cmake -E echo ... > file` together with `>>` redirects in
 * add_custom_command. CMake runs those without a shell, so the redirects were passed as
 * literal arguments, the file was never written with content, and an empty placeholder
 * "succeeded" its way into the APK. The app then read no path back, layoutMatches() was
 * false, and DFRoot refused to run — a build bug that looked exactly like a root bug.
 *
 * Doing it in C means one process, one exit code, and a build that fails loudly if the
 * marker cannot be written.
 *
 * Usage: pathmarker <output-file> <payload-dir>
 */
#include <stdio.h>
#include <string.h>

int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "usage: %s <output-file> <payload-dir>\n", argv[0]);
        return 2;
    }
    const char *out = argv[1];
    const char *dir = argv[2];

    /* Staging layout is <payload-dir>/bootstrap: a sibling of files/, which is what
     * upstream DFRoot's kernel module is written against. */
    char line[512];
    int n = snprintf(line, sizeof(line), "%s/bootstrap\n", dir);
    if (n <= 0 || (size_t)n >= sizeof(line)) {
        fprintf(stderr, "payload path too long: %s\n", dir);
        return 1;
    }

    FILE *f = fopen(out, "wb");
    if (!f) {
        fprintf(stderr, "cannot open %s for writing\n", out);
        return 1;
    }

    /* A minimal ELF64 header so the file is a loadable shared object. AGP packages
     * lib*.so from the CMake output directory, and the whole point is to get a file into
     * nativeLibraryDir next to the payload it describes. */
    static const unsigned char elf_stub[64] = {
        0x7f, 'E', 'L', 'F', 2, 1, 1, 0,   /* magic, ELFCLASS64, data, version, osabi */
        0, 0, 0, 0, 0, 0, 0, 0,            /* pad to e_type */
        3, 0,                              /* e_type = ET_DYN */
        0xb7, 0,                           /* e_machine = EM_AARCH64 */
        1, 0, 0, 0,                        /* e_version */
        0, 0, 0, 0, 0, 0, 0, 0,            /* e_entry */
        0, 0, 0, 0, 0, 0, 0, 0,            /* e_phoff, e_shoff */
        0, 0, 0, 0,                        /* e_flags */
        0x40, 0,                           /* e_ehsize = 64 */
        0, 0, 0, 0, 0, 0,                  /* e_phentsize..e_shnum */
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 /* e_shstrndx + tail */
    };
    if (fwrite(elf_stub, 1, sizeof(elf_stub), f) != sizeof(elf_stub)) {
        fclose(f);
        return 1;
    }
    if (fwrite(line, 1, (size_t)n, f) != (size_t)n) {
        fclose(f);
        return 1;
    }
    if (fclose(f) != 0) {
        return 1;
    }
    return 0;
}
