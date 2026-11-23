# Disposable synthetic-upgrade QA ONLY. Injected solely by
# tools/qa/synthetic-qa.init.gradle (which itself only runs under -I with
# -PcivilegSyntheticQa=true). No production build file or shipping keep rule
# references this file.
#
# Member-level harness ABI retention for the minified androidTest target,
# derived from the exact 49515d36 freeRelease + freeReleaseAndroidTest APK
# pair against kotlin-stdlib-2.4.21 (sha256
# bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3) and
# kotlinx-coroutines-core-jvm-1.10.2 (sha256
# 5ca175b38df331fd64155b35cd8cae1251fa9ee369709b36d42e0a288ccce3fd).
# Semantics: a bare '-keep class X' prevents shrinking AND optimization of
# the class and may retain constructors; members are still removable unless
# pinned, so every referenced member including constructors and
# override-contract members of kept supertypes is pinned explicitly.
# kotlin.LazyKt stays in qa-lazykt-facade-keep.pro (retained, unchanged).
# Deliberately plain -keep entries: no allowoptimization, no
# allowobfuscation, no allowshrinking, no blanket kotlin.* keeps.
#
-keep class kotlin.ExceptionsKt
-keep class kotlin.ExceptionsKt__ExceptionsKt
-keep class kotlin.ExceptionsKt__ExceptionsKt { void addSuppressed(java.lang.Throwable, java.lang.Throwable); }  # declared on kotlin.ExceptionsKt__ExceptionsKt
-keep class kotlin.KotlinNothingValueException
-keep class kotlin.KotlinNothingValueException { KotlinNothingValueException(); }
-keep class kotlin.NoWhenBranchMatchedException
-keep class kotlin.NoWhenBranchMatchedException { NoWhenBranchMatchedException(); }
-keep class kotlin.ResultKt
-keep class kotlin.ResultKt { java.lang.Object createFailure(java.lang.Throwable); }
-keep class kotlin.ResultKt { void throwOnFailure(java.lang.Object); }
-keep class kotlin.TuplesKt
-keep class kotlin.TuplesKt { kotlin.Pair to(java.lang.Object, java.lang.Object); }
-keep class kotlin.collections.AbstractIterator
-keep class kotlin.collections.AbstractIterator { AbstractIterator(); }
-keep class kotlin.collections.AbstractIterator { void done(); }
-keep class kotlin.collections.AbstractIterator { void setNext(java.lang.Object); }
-keep class kotlin.collections.AbstractIterator { void computeNext(); }  # override contract: subclass TreeIterables$TreeTraversalIterable$1; overrides this member
-keep class kotlin.collections.MapsKt
-keep class kotlin.collections.MapsKt___MapsKt
-keep class kotlin.collections.MapsKt___MapsJvmKt
-keep class kotlin.collections.MapsKt__MapsKt
-keep class kotlin.collections.MapsKt__MapsJVMKt
-keep class kotlin.collections.MapsKt__MapWithDefaultKt
-keep class kotlin.collections.MapsKt__MapsKt { java.util.Map emptyMap(); }  # declared on kotlin.collections.MapsKt__MapsKt
-keep class kotlin.collections.MapsKt__MapsKt { java.util.Map mapOf(kotlin.Pair[]); }  # declared on kotlin.collections.MapsKt__MapsKt  # dex m0 = mapOf (callsite+source grounded)
-keep class kotlin.collections.MapsKt__MapsKt { java.util.Map mutableMapOf(kotlin.Pair[]); }  # declared on kotlin.collections.MapsKt__MapsKt  # dex n0 = mutableMapOf (callsite+source grounded)
-keep class kotlin.comparisons.ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt___ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt___ComparisonsJvmKt
-keep class kotlin.comparisons.ComparisonsKt__ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt__ComparisonsKt { int compareValues(java.lang.Comparable, java.lang.Comparable); }  # declared on kotlin.comparisons.ComparisonsKt__ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt__ComparisonsKt { int compareValuesBy(java.lang.Object, java.lang.Object, kotlin.jvm.functions.Function1[]); }  # declared on kotlin.comparisons.ComparisonsKt__ComparisonsKt
-keep class kotlin.coroutines.ContinuationInterceptor$DefaultImpls
-keep class kotlin.coroutines.ContinuationInterceptor$DefaultImpls { kotlin.coroutines.CoroutineContext.Element get(kotlin.coroutines.ContinuationInterceptor, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlin.coroutines.ContinuationInterceptor$DefaultImpls { kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.ContinuationInterceptor, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlin.coroutines.ContinuationInterceptor$DefaultImpls { void releaseInterceptedContinuation(kotlin.coroutines.ContinuationInterceptor, kotlin.coroutines.Continuation); }
-keep class kotlin.coroutines.ContinuationKt
-keep class kotlin.coroutines.ContinuationKt { kotlin.coroutines.Continuation createCoroutine(kotlin.jvm.functions.Function1, kotlin.coroutines.Continuation); }
-keep class kotlin.coroutines.CoroutineContext$Element$DefaultImpls
-keep class kotlin.coroutines.CoroutineContext$Element$DefaultImpls { java.lang.Object fold(kotlin.coroutines.CoroutineContext.Element, java.lang.Object, kotlin.jvm.functions.Function2); }
-keep class kotlin.coroutines.CoroutineContext$Element$DefaultImpls { kotlin.coroutines.CoroutineContext.Element get(kotlin.coroutines.CoroutineContext.Element, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlin.coroutines.CoroutineContext$Element$DefaultImpls { kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext.Element, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlin.coroutines.CoroutineContext$Element$DefaultImpls { kotlin.coroutines.CoroutineContext plus(kotlin.coroutines.CoroutineContext.Element, kotlin.coroutines.CoroutineContext); }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt { java.lang.Object getCOROUTINE_SUSPENDED(); }  # declared on kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt { kotlin.coroutines.Continuation intercepted(kotlin.coroutines.Continuation); }  # declared on kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt
-keep class kotlin.coroutines.jvm.internal.DebugProbesKt
-keep class kotlin.coroutines.jvm.internal.DebugProbesKt { void probeCoroutineSuspended(kotlin.coroutines.Continuation); }
-keep class kotlin.coroutines.jvm.internal.SuspendFunction
-keep class kotlin.enums.EnumEntriesKt
-keep class kotlin.enums.EnumEntriesKt { kotlin.enums.EnumEntries enumEntries(java.lang.Enum[]); }
-keep class kotlin.io.CloseableKt
-keep class kotlin.io.CloseableKt { void closeFinally(java.io.Closeable, java.lang.Throwable); }
-keep class kotlin.io.FilesKt
-keep class kotlin.io.FilesKt__UtilsKt
-keep class kotlin.io.FilesKt__FileTreeWalkKt
-keep class kotlin.io.FilesKt__FileReadWriteKt
-keep class kotlin.io.FilesKt__FilePathComponentsKt
-keep class kotlin.io.FilesKt__FileReadWriteKt { java.lang.String readText$default(java.io.File, java.nio.charset.Charset, int, java.lang.Object); }  # declared on kotlin.io.FilesKt__FileReadWriteKt
-keep class kotlin.io.TextStreamsKt
-keep class kotlin.io.TextStreamsKt { java.lang.String readText(java.io.Reader); }
-keep class kotlin.jvm.internal.InlineMarker
-keep class kotlin.jvm.internal.InlineMarker { void finallyEnd(int); }
-keep class kotlin.jvm.internal.InlineMarker { void finallyStart(int); }
-keep class kotlin.jvm.internal.StringCompanionObject
-keep class kotlin.jvm.internal.StringCompanionObject { kotlin.jvm.internal.StringCompanionObject INSTANCE; }
-keep class kotlin.ranges.LongRange
-keep class kotlin.ranges.LongRange { LongRange(long, long); }
-keep class kotlin.ranges.LongProgression { long getFirst(); }  # declared on kotlin.ranges.LongProgression
-keep class kotlin.ranges.LongProgression { long getLast(); }  # declared on kotlin.ranges.LongProgression
-keep class kotlin.text.Regex
-keep class kotlin.text.Regex { Regex(java.lang.String); }
-keep class kotlin.text.Regex { java.lang.String replace(java.lang.CharSequence, java.lang.String); }
-keep class kotlin.time.AbstractLongTimeSource
# Delta v6 (QA-accepted 2026-11-23): the simple-spelling constructor rule
# below was REPLACED, not appended: the original line
#   -keep class kotlin.time.AbstractLongTimeSource { AbstractLongTimeSource(kotlin.time.DurationUnit); }
# was present in the effective v5 configuration (configuration.txt lines
# 347-349) yet usage.txt lists "public void <init>(kotlin.time.DurationUnit)"
# among the class's REMOVED members and both FrameEntryTest cases failed at
# TestCoroutineScheduler$timeSource$1.<init> with NoSuchMethodError
# <init>(Lc10;)V (run 45118067467): parser acceptance was not R8 matching.
# Corrected to the explicit <init> spelling. The class pin (-keep class
# kotlin.time.AbstractLongTimeSource, which held: freeRelease mapping keeps
# the name) and the read() override contract below are preserved unchanged;
# the class stays abstract - this does not force concrete. Original member
# verified in kotlin-stdlib-2.4.21.jar (sha256
# bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3):
# public void <init>(kotlin.time.DurationUnit). Rebuild-inspect: rebuilt
# public ctor with mapped DurationUnit owner plus the test subclass
# invoke-direct joint descriptor, then the unchanged 5 tests.
-keep class kotlin.time.AbstractLongTimeSource { public <init>(kotlin.time.DurationUnit); }
-keep class kotlin.time.AbstractLongTimeSource { long read(); }  # override contract: subclass TestCoroutineScheduler$timeSource$1; overrides this member
-keep class kotlin.time.Duration$Companion
-keep class kotlin.time.Duration$Companion { long parse-UwyO8pc(java.lang.String); }
-keep class kotlin.time.DurationKt
-keep class kotlin.time.DurationKt { long toDuration(int, kotlin.time.DurationUnit); }
-keep class kotlin.time.DurationKt { long toDuration(long, kotlin.time.DurationUnit); }
-keep class kotlin.time.TimeSource$WithComparableMarks
-keep class kotlinx.coroutines.BuildersKt
-keep class kotlinx.coroutines.BuildersKt { kotlinx.coroutines.Deferred async(kotlinx.coroutines.CoroutineScope, kotlin.coroutines.CoroutineContext, kotlinx.coroutines.CoroutineStart, kotlin.jvm.functions.Function2); }
-keep class kotlinx.coroutines.BuildersKt { kotlinx.coroutines.Deferred async$default(kotlinx.coroutines.CoroutineScope, kotlin.coroutines.CoroutineContext, kotlinx.coroutines.CoroutineStart, kotlin.jvm.functions.Function2, int, java.lang.Object); }
-keep class kotlinx.coroutines.BuildersKt { kotlinx.coroutines.Job launch$default(kotlinx.coroutines.CoroutineScope, kotlin.coroutines.CoroutineContext, kotlinx.coroutines.CoroutineStart, kotlin.jvm.functions.Function2, int, java.lang.Object); }
-keep class kotlinx.coroutines.BuildersKt { java.lang.Object runBlocking(kotlin.coroutines.CoroutineContext, kotlin.jvm.functions.Function2); }
-keep class kotlinx.coroutines.BuildersKt { java.lang.Object runBlocking$default(kotlin.coroutines.CoroutineContext, kotlin.jvm.functions.Function2, int, java.lang.Object); }
-keep class kotlinx.coroutines.BuildersKt { java.lang.Object withContext(kotlin.coroutines.CoroutineContext, kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.CancellableContinuation$DefaultImpls
-keep class kotlinx.coroutines.CancellableContinuation$DefaultImpls { boolean cancel$default(kotlinx.coroutines.CancellableContinuation, java.lang.Throwable, int, java.lang.Object); }
-keep class kotlinx.coroutines.CancellableContinuationKt
-keep class kotlinx.coroutines.CancellableContinuationKt { void disposeOnCancellation(kotlinx.coroutines.CancellableContinuation, kotlinx.coroutines.DisposableHandle); }
-keep class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls
-keep class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls { java.lang.Object fold(kotlinx.coroutines.CoroutineExceptionHandler, java.lang.Object, kotlin.jvm.functions.Function2); }
-keep class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls { kotlin.coroutines.CoroutineContext.Element get(kotlinx.coroutines.CoroutineExceptionHandler, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls { kotlin.coroutines.CoroutineContext minusKey(kotlinx.coroutines.CoroutineExceptionHandler, kotlin.coroutines.CoroutineContext.Key); }
-keep class kotlinx.coroutines.CoroutineExceptionHandler$DefaultImpls { kotlin.coroutines.CoroutineContext plus(kotlinx.coroutines.CoroutineExceptionHandler, kotlin.coroutines.CoroutineContext); }
-keep class kotlinx.coroutines.CoroutineExceptionHandlerKt
-keep class kotlinx.coroutines.CoroutineExceptionHandlerKt { void handleCoroutineException(kotlin.coroutines.CoroutineContext, java.lang.Throwable); }
-keep class kotlinx.coroutines.CoroutineScopeKt
-keep class kotlinx.coroutines.CoroutineScopeKt { kotlinx.coroutines.CoroutineScope CoroutineScope(kotlin.coroutines.CoroutineContext); }
-keep class kotlinx.coroutines.CoroutineScopeKt { void cancel$default(kotlinx.coroutines.CoroutineScope, java.util.concurrent.CancellationException, int, java.lang.Object); }
-keep class kotlinx.coroutines.CoroutineScopeKt { boolean isActive(kotlinx.coroutines.CoroutineScope); }
-keep class kotlinx.coroutines.DebugKt
-keep class kotlinx.coroutines.DebugKt { boolean getRECOVER_STACK_TRACES(); }
-keep class kotlinx.coroutines.Deferred
-keep class kotlinx.coroutines.Deferred { java.lang.Object await(kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.Deferred { java.lang.Throwable getCompletionExceptionOrNull(); }
-keep class kotlinx.coroutines.Delay$DefaultImpls
-keep class kotlinx.coroutines.Delay$DefaultImpls { java.lang.Object delay(kotlinx.coroutines.Delay, long, kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.DelayKt
-keep class kotlinx.coroutines.DelayKt { java.lang.Object delay(long, kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.DelayWithTimeoutDiagnostics
-keep class kotlinx.coroutines.DelayWithTimeoutDiagnostics { java.lang.String timeoutMessage-LRDsOJo(long); }  # override contract: subclass TestDispatcher; overrides this member
-keep class kotlinx.coroutines.ExecutorsKt
-keep class kotlinx.coroutines.ExecutorsKt { kotlinx.coroutines.CoroutineDispatcher from(java.util.concurrent.Executor); }
-keep class kotlinx.coroutines.Job$DefaultImpls
-keep class kotlinx.coroutines.Job$DefaultImpls { void cancel$default(kotlinx.coroutines.Job, java.util.concurrent.CancellationException, int, java.lang.Object); }
-keep class kotlinx.coroutines.Job$DefaultImpls { kotlinx.coroutines.DisposableHandle invokeOnCompletion$default(kotlinx.coroutines.Job, boolean, boolean, kotlin.jvm.functions.Function1, int, java.lang.Object); }
-keep class kotlinx.coroutines.JobKt
-keep class kotlinx.coroutines.JobKt { kotlinx.coroutines.CompletableJob Job$default(kotlinx.coroutines.Job, int, java.lang.Object); }
-keep class kotlinx.coroutines.JobKt { java.lang.Object cancelAndJoin(kotlinx.coroutines.Job, kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.JobKt { kotlinx.coroutines.Job getJob(kotlin.coroutines.CoroutineContext); }
-keep class kotlinx.coroutines.MainCoroutineDispatcher
-keep class kotlinx.coroutines.MainCoroutineDispatcher { MainCoroutineDispatcher(); }
-keep class kotlinx.coroutines.CoroutineDispatcher { void dispatch(kotlin.coroutines.CoroutineContext, java.lang.Runnable); }  # declared on kotlinx.coroutines.CoroutineDispatcher
-keep class kotlinx.coroutines.MainCoroutineDispatcher { kotlinx.coroutines.MainCoroutineDispatcher getImmediate(); }
-keep class kotlinx.coroutines.CoroutineDispatcher { boolean isDispatchNeeded(kotlin.coroutines.CoroutineContext); }  # override contract: subclass TestMainDispatcher; overrides this member
-keep class kotlinx.coroutines.SupervisorKt
-keep class kotlinx.coroutines.SupervisorKt { kotlinx.coroutines.CompletableJob SupervisorJob$default(kotlinx.coroutines.Job, int, java.lang.Object); }
-keep class kotlinx.coroutines.TimeoutKt
-keep class kotlinx.coroutines.TimeoutKt { java.lang.Object withTimeout-KLykuaI(long, kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.YieldKt
-keep class kotlinx.coroutines.YieldKt { java.lang.Object yield(kotlin.coroutines.Continuation); }
-keep class kotlinx.coroutines.channels.ChannelKt
-keep class kotlinx.coroutines.channels.ChannelKt { kotlinx.coroutines.channels.Channel Channel$default(int, kotlinx.coroutines.channels.BufferOverflow, kotlin.jvm.functions.Function1, int, java.lang.Object); }
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl { kotlinx.coroutines.debug.internal.DebugProbesImpl INSTANCE; }
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl { void dumpCoroutines(java.io.PrintStream); }
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl { void install$kotlinx_coroutines_core(); }
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl { boolean isInstalled$kotlinx_coroutines_debug(); }
-keep class kotlinx.coroutines.debug.internal.DebugProbesImpl { void uninstall$kotlinx_coroutines_core(); }
-keep class kotlinx.coroutines.internal.CoroutineExceptionHandlerImpl_commonKt
-keep class kotlinx.coroutines.internal.CoroutineExceptionHandlerImpl_commonKt { void handleUncaughtCoroutineException(kotlin.coroutines.CoroutineContext, java.lang.Throwable); }
-keep class kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed
-keep class kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed { kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed INSTANCE; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory$DefaultImpls
-keep class kotlinx.coroutines.internal.MainDispatcherFactory$DefaultImpls { java.lang.String hintOnError(kotlinx.coroutines.internal.MainDispatcherFactory); }
-keep class kotlinx.coroutines.internal.MainDispatchersKt
-keep class kotlinx.coroutines.internal.MainDispatchersKt { boolean isMissing(kotlinx.coroutines.MainCoroutineDispatcher); }
-keep class kotlinx.coroutines.internal.MainDispatchersKt { kotlinx.coroutines.MainCoroutineDispatcher tryCreateDispatcher(kotlinx.coroutines.internal.MainDispatcherFactory, java.util.List); }
-keep class kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory
-keep class kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory { kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory INSTANCE; }
-keep class kotlinx.coroutines.internal.ThreadSafeHeapNode
-keep class kotlinx.coroutines.internal.ThreadSafeHeapNode { kotlinx.coroutines.internal.ThreadSafeHeap getHeap(); }  # override contract: subclass TestDispatchEvent; overrides this member
-keep class kotlinx.coroutines.internal.ThreadSafeHeapNode { int getIndex(); }  # override contract: subclass TestDispatchEvent; overrides this member
-keep class kotlinx.coroutines.internal.ThreadSafeHeapNode { void setHeap(kotlinx.coroutines.internal.ThreadSafeHeap); }  # override contract: subclass TestDispatchEvent; overrides this member
-keep class kotlinx.coroutines.internal.ThreadSafeHeapNode { void setIndex(int); }  # override contract: subclass TestDispatchEvent; overrides this member
-keep class kotlinx.coroutines.selects.OnTimeoutKt
-keep class kotlinx.coroutines.selects.OnTimeoutKt { void onTimeout-8Mi8wO0(kotlinx.coroutines.selects.SelectBuilder, long, kotlin.jvm.functions.Function1); }
-keep class kotlinx.coroutines.selects.SelectBuilder
-keep class kotlinx.coroutines.selects.SelectBuilder { void invoke(kotlinx.coroutines.selects.SelectClause0, kotlin.jvm.functions.Function1); }
-keep class kotlinx.coroutines.selects.SelectBuilder { void invoke(kotlinx.coroutines.selects.SelectClause1, kotlin.jvm.functions.Function2); }
-keep class kotlinx.coroutines.selects.SelectClause0
-keep class kotlinx.coroutines.selects.SelectClause1
-keep class kotlinx.coroutines.selects.SelectImplementation
-keep class kotlinx.coroutines.selects.SelectImplementation { SelectImplementation(kotlin.coroutines.CoroutineContext); }
-keep class kotlinx.coroutines.selects.SelectImplementation { java.lang.Object doSelect(kotlin.coroutines.Continuation); }
-keep class kotlin.ExceptionsKt__ExceptionsKt  # supertype/interface of kept class kotlin.ExceptionsKt
-keep class kotlin.collections.MapsKt__MapWithDefaultKt  # supertype/interface of kept class kotlin.collections.MapsKt__MapsKt
-keep class kotlin.collections.MapsKt__MapsJVMKt  # supertype/interface of kept class kotlin.collections.MapsKt__MapsKt
-keep class kotlin.collections.MapsKt___MapsJvmKt  # supertype/interface of kept class kotlin.collections.MapsKt
-keep class kotlin.collections.MapsKt___MapsKt  # supertype/interface of kept class kotlin.collections.MapsKt
-keep class kotlin.comparisons.ComparisonsKt__ComparisonsKt  # supertype/interface of kept class kotlin.comparisons.ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt___ComparisonsJvmKt  # supertype/interface of kept class kotlin.comparisons.ComparisonsKt
-keep class kotlin.comparisons.ComparisonsKt___ComparisonsKt  # supertype/interface of kept class kotlin.comparisons.ComparisonsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsJvmKt  # supertype/interface of kept class kotlin.coroutines.intrinsics.IntrinsicsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt  # supertype/interface of kept class kotlin.coroutines.intrinsics.IntrinsicsKt
-keep class kotlin.io.FilesKt__FilePathComponentsKt  # supertype/interface of kept class kotlin.io.FilesKt
-keep class kotlin.io.FilesKt__FileReadWriteKt  # supertype/interface of kept class kotlin.io.FilesKt
-keep class kotlin.io.FilesKt__FileTreeWalkKt  # supertype/interface of kept class kotlin.io.FilesKt
-keep class kotlin.io.FilesKt__UtilsKt  # supertype/interface of kept class kotlin.io.FilesKt
-keep class kotlin.ranges.LongProgression  # supertype/interface of kept class kotlin.ranges.LongRange
-keep class kotlin.ranges.OpenEndRange  # supertype/interface of kept class kotlin.ranges.LongRange
-keep class kotlin.time.TimeSource  # supertype/interface of kept class kotlin.time.TimeSource$WithComparableMarks
-keep class kotlinx.coroutines.CancelHandler  # supertype/interface of kept class kotlinx.coroutines.selects.SelectImplementation
-keep class kotlinx.coroutines.selects.SelectClause  # supertype/interface of kept class kotlinx.coroutines.selects.SelectClause0
-keep class kotlinx.coroutines.selects.SelectInstance  # supertype/interface of kept class kotlinx.coroutines.selects.SelectImplementation
-keep class kotlinx.coroutines.selects.SelectInstanceInternal  # supertype/interface of kept class kotlinx.coroutines.selects.SelectImplementation

# ── Finite harness ABI delta (derived from run 45087024831 exact retained failures) ──
# One rule per observed category; evidence in the review packet (exact retained
# failures of run 45087024831 and the review rounds that followed it).
# Category 1: test dex holds 53 live invoke-static refs to these 4 members;
# the class exists in neither APK.
-keep class kotlin.jvm.internal.Intrinsics {
    boolean areEqual(java.lang.Object, java.lang.Object);
    void reifiedOperationMarker(int, java.lang.String);
    void throwNpe();
    void throwUninitializedPropertyAccessException(java.lang.String);
}
# Category 2: test invokes JsonParser.parseString(String); class removed app-side.
# Header verified against the exact classpath jar (gson 2.13.1):
# public static com.google.gson.JsonElement parseString(java.lang.String).
-keep class com.google.gson.JsonParser {
    public static com.google.gson.JsonElement parseString(java.lang.String);
}
# Category 3: androidx test framework invokes complete()Z on the interface the
# app renamed to Lkn; while removing both interface methods (usage.txt).
-keep interface kotlinx.coroutines.CompletableJob {
    boolean complete();
    boolean completeExceptionally(java.lang.Throwable);
}

# ── Delta v2 (QA-accepted 2026-11-23): two exact observed members ──
# Category 4: test dex invokes Gson.toJsonTree(Object); both overloads were
# removed app-side (usage.txt). Only the observed single-arg overload is kept.
# Header verified against the exact classpath gson 2.13.1 jar.
-keep class com.google.gson.Gson {
    public com.google.gson.JsonElement toJsonTree(java.lang.Object);
}
# Category 5: androidx.compose.ui.test.IdlingResourceRegistry invokes
# Dispatchers.getMain(); the class was renamed to sz with the method dropped
# (no mapping entry, no usage.txt removal record; runtime NSME).
# Header verified against the exact classpath coroutines-core-jvm 1.10.2 jar.
-keep class kotlinx.coroutines.Dispatchers {
    public static final kotlinx.coroutines.MainCoroutineDispatcher getMain();
}

# ── Delta v3 (QA-accepted 2026-11-23): exact 3-member external implementation contract ──
# Category 6: run 45099351385, both FrameEntryTest cases fail at compose-rule
# init with AbstractMethodError "kotlinx.coroutines.MainCoroutineDispatcher
# kotlinx.coroutines.internal.MainDispatcherFactory.a()" at
# MainDispatchersKt.tryCreateDispatcher <- wq0.<clinit> (renamed
# MainDispatcherLoader). Mechanism is parameter signature drift across the two
# separately minified APKs, NOT inconsistent rename: member renames are
# consistent (createDispatcher->a, hintOnError->b, getLoadPriority->c) but the
# app-side pass also stripped createDispatcher's unused List parameter
# (candidate interface classdef declares no-arg a(); implementor z3 overrides
# no-arg), while the instrumentation factory implements
# a(Ljava/util/List;) and defines no interface copy of its own, so its
# override never binds to the loaded stripped interface. Retaining the three
# members with their exact original signatures pins both names and parameter
# signatures across the app-side hierarchy. Plain -keep: no allowoptimization,
# no allowobfuscation, no allowshrinking; no library blanket. Headers verified
# against the exact classpath kotlinx-coroutines-core-jvm 1.10.2 jar:
# public abstract kotlinx.coroutines.MainCoroutineDispatcher createDispatcher(java.util.List)
# public abstract java.lang.String hintOnError()
# public abstract int getLoadPriority()
-keep interface kotlinx.coroutines.internal.MainDispatcherFactory {
    public abstract kotlinx.coroutines.MainCoroutineDispatcher createDispatcher(java.util.List);
    public abstract java.lang.String hintOnError();
    public abstract int getLoadPriority();
}

# ── Delta v4 (QA-accepted 2026-11-23): exact one field ──
# Category 7: run 45105190528, both FrameEntryTest cases fail at
# androidx.compose.ui.test.AndroidComposeUiTestEnvironment.<init> with
# java.lang.NoSuchFieldError: No field Key of type
# Lkotlin/coroutines/ContinuationInterceptor$Key; in class Lrt
# (kotlin.coroutines.ContinuationInterceptor -> rt, freeRelease mapping line
# 1817114). usage.txt records this exact field as removed app-side; the
# instrumentation dex references Lrt;.Key:Lkotlin/coroutines/ContinuationInterceptor$Key;.
# Mechanism: app-side shrinking removed the companion Key field while the
# separately minified test APK still references it; class rename itself is
# consistent across both APKs. Field header verified against the exact
# classpath kotlin-stdlib-2.4.21.jar (sha256
# bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3):
# flags 0x19 public static final Key Lkotlin/coroutines/ContinuationInterceptor$Key;.
# Plain -keep of the exact one field: no all-members, no package, no
# production change. The nested Key TYPE is deliberately not kept here; the
# rebuild-inspect checks whether it survives on its own.
-keep class kotlin.coroutines.ContinuationInterceptor {
    public static final kotlin.coroutines.ContinuationInterceptor$Key Key;
}

# ── Delta v5 (QA-accepted 2026-11-23): exact one constructor ──
# Category 7: run 45109485074 (v4 head 89c0b3a9), both FrameEntryTest cases
# fail at kotlinx.coroutines.test.TestCoroutineScheduler.<init> (obf :6 =
# source :34, the `events = ThreadSafeHeap<TimedTask>()` init) with
# java.lang.InstantiationError: jv1, reached via UnconfinedTestDispatcher <-
# AndroidComposeUiTestEnvironment.<init> <- createAndroidComposeRule <-
# FrameEntryTest.<init>: test-environment construction only, not test body.
# Mechanism (dex/mapping verified): candidate.apk classes3.dex defines
# kotlinx.coroutines.internal.ThreadSafeHeap -> jv1 with flags 0x401
# (public|abstract), super Object, no interfaces, and holds NO <init> method
# ref for it; instrumentation.apk defines the class nowhere yet holds the
# method ref Ljv1;.<init>()V for the intended `new`. Original library jar
# kotlinx-coroutines-core-jvm-1.10.2.jar (sha256
# 5ca175b38df331fd64155b35cd8cae1251fa9ee369709b36d42e0a288ccce3fd) has
# ThreadSafeHeap concrete (access 0x21) with public <init>()V: the target R8
# pass abstracted/stripped it, the test pass stripped the class but kept the
# call site, and the runtime new-instance binds to the abstract target shell.
# Plain -keep of the class with ONLY the public no-arg constructor: restores
# the concrete instantiable class + ctor in the target; no allowoptimization/
# allowshrinking/allowobfuscation, no all-members, no package, no production
# change. The nested $Key holder rename (rt) was verified consistent across
# both APKs in v4 and needs no further keep.
-keep class kotlinx.coroutines.internal.ThreadSafeHeap {
    public <init>();
}
