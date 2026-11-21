package org.texboobcat.wcpu.region;

final class Failures {

    private Failures() {
    }

    // The failure and, when it is only a wrapper, the exception underneath: an
    // ExceptionInInitializerError says nothing until the cause is shown next to it.
    static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == failure ? failure.toString() : failure + " <- " + root;
    }
}
