# Disposable synthetic-upgrade QA ONLY. Injected solely by
# tools/qa/synthetic-qa.init.gradle (which itself only runs under -I with
# -PcivilegSyntheticQa=true). No production build file or shipping keep rule
# references this file.
#
# Problem: the androidx.test runner reaches kotlin.LazyKt.lazy through a
# direct static invoke compiled into androidx.test.platform.io.TestDirCalculator,
# but R8 optimization inlines the multifile facade parts into call sites and
# drops the facade, so instrumentation fails with NoClassDefFoundError
# kotlin/LazyKt before any test runs. Facade-name-only keeps are not
# sufficient against remapped member signatures, so the full member
# descriptors are pinned through the exact facade/superclass hierarchy and
# the Lazy contract.
#
# Deliberately plain -keep entries: no allowoptimization, no allowobfuscation,
# no allowshrinking, and no blanket kotlin.* or application keeps.
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
-keep interface kotlin.Lazy { *; }
