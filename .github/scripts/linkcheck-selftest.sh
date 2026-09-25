#!/usr/bin/env bash
# Self-test for LinkCheck.java: a tiny "library" changes between two versions in every way the check
# must catch, and a "mod" built against the first version is checked against the second.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
cd "${work}"
mkdir -p v1 v2 mod

cat > v1/Lib.java <<'JAVA'
package lib;
public abstract class Lib {
    public int field;
    public int size() { return 1; }
    public void draw(int x) { }
    public static void util() { }
    public void kept() { }
}
JAVA
cat > v2/Lib.java <<'JAVA'
package lib;
public abstract class Lib {
    public long size() { return 1; }
    public void draw(long x) { }
    public void util() { }
    public void kept() { }
    public abstract void tick();
}
JAVA
cat > Mod.java <<'JAVA'
package mod;
public class Mod extends lib.Lib {
    @Override public void draw(int x) { }
    public int go() { lib.Lib.util(); kept(); return size() + field; }
}
JAVA

javac -d v1 v1/Lib.java
javac -d v2 v2/Lib.java
javac -cp v1 -d mod Mod.java
jar cf mod.jar -C mod .
echo "${work}/v1" > ref.txt
echo "${work}/v2" > target.txt

# Same classpath on both sides: nothing to report
java "${here}/LinkCheck.java" mod.jar ref.txt ref.txt

# Changed library: every change must be reported, the unchanged method must not
set +e
java "${here}/LinkCheck.java" mod.jar ref.txt target.txt > out.txt
status=$?
set -e
cat out.txt
[ "${status}" -eq 1 ] || { echo "expected exit code 1, got ${status}"; exit 1; }
for expected in '.size()I' 'lib/Lib.util()V' '.field:I' 'override mod/Mod.draw(I)V' 'abstract methods left in mod/Mod'; do
    grep -F "DIFF" out.txt | grep -qF "${expected}" || { echo "not reported: ${expected}"; exit 1; }
done
if grep -F "DIFF" out.txt | grep -qF 'kept()V'; then
    echo "reported an unchanged method"
    exit 1
fi
echo "LinkCheck self-test passed"
