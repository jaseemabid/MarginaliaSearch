# Modified Marginalia Search sources for replicating likely compiler bug in GraalVM

See the blog post [Deep Bug](https://www.marginalia.nu/log/a_104_dep_bug/).

## Steps to reproduce:

1. Clone the repo https://github.com/MarginaliaSearch/MarginaliaSearch

2. Switch to the branch jvm-bug-replicator

3. Run ./gradlew assemble

(gradle's been pretty flaky with JDK 22 so you may need to use a system
with both JDK 21 and 22 installed for its sake... )

4. This will build among other artifacts
   code/processes/index-constructor-process/build/distributions/index-construction-process.tar
   -- this is the one you want.

5. Download https://downloads.marginalia.nu/test-data-jvm-bug.tar.gz
   and extract it somewhere. This is ~ 10 GB of processed Wikipedia data on a binary soup format.

6.  Make an output dir for the program.  This is where intermediate
    memory mapped files go, and you can also expect it to fill up with a
    few dozen GB of garbage over time if the program fails.

```
$ mkdir output-dir
```

7. Unpack index-construction-process.tar

8. Run

```
$ bin/index-construction-process test-data-jvm-bug/ output-dir/
```

This runs the affected process.  With any "luck", it should fail
with an exception like `java.lang.IllegalStateException: Impossible state`

This doesn't always happen, so you may need to run it a few times.
Total run time should be about 15 minutes, and it may need a decent
amount of RAM, but no more than 32 GB.  No need to mess with Xmx or
anything like that, almost all of the allocation is off heap.

## Notes

* You can disable sun.misc.Unsafe by passing
INDEX_CONSTRUCTION_PROCESS_OPTS="--enable-preview
-Dsystem.noSunMiscUnsafe=TRUE" ... ; the bug still manifests using the MemorySegment implementation, but I
guess it might be good to know for debugging.  The only preview feature
being used is string templates.

* The bug has been replicated on multiple machines, all Linux and all on a Zen architecture,
but different versions of both the CPU and the OS.

* It goes away with a openjdk VM, but manifests on GraalVM.
