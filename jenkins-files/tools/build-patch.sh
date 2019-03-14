#!/bin/bash -x
echo "Building OpenJDK patch..."

startDir="$(pwd)"

jdkDir=$(readlink -m $(dirname $(readlink -f $(which java)))/../..)
echo "JDK directory: ${jdkDir}"

unzip -qd src/ ${jdkDir}/src.zip

patchJar() {
    jarFile="$1"
    patchFile="$2"
    echo "Attempting to patch ${jarFile} using ${patchFile}..."
    # Patch the JDK source files
    patch -d src/ -p1 < ${patchFile}
    mkdir out-${jarFile//.jar/}/
    # Re-compile any file affected by the patch
    cd src/
    javac -d ../out-${jarFile//.jar/}/ -XDignore.symbol.file $(grep "diff " ../${patchFile} | awk '{print $4}' | sed 's/^p11-patched\///' | tr '\r\n' ' ') || exit 1
    cd ..
    # Make a local copy of the JAR from the JDK
    cp "${jdkDir}/jre/lib/ext/${jarFile}" ${jarFile}
    # Update local copy of the JAR with the patched and compiled files
    jar uvf ${jarFile} -C out-${jarFile//.jar/}/ . || exit 1
}

echo "PWD: $PWD"
echo "Contents of PWD"
ls -ltra $PWD

echo "Contents of $HOME"
ls -ltra $HOME

echo "Contents of $HOME/binaries"
ls -ltra $HOME/binaries

ls -ltra jenkins-files/containers/$CONTAINER/

patchJar sunec.jar     jenkins-files/containers/$CONTAINER/binaries/openJDK8-sunec.patch
patchJar sunpkcs11.jar jenkins-files/containers/$CONTAINER/binaries/openJDK8-sunpkcs11.patch

echo "PWD after patch"
ls -ltra $PWD

ls -ltra $PWD/out-sunec
ls -ltra $PWD/out-sunpkcs11/sun/security/pkcs11

mkdir artifacts/

artifactBaseName=$(basename $jdkDir)
#export XZ_OPT="-7e --threads=0"
#time tar -Ixz --directory=. -cvnf artifacts/openjdk-patch.tar.xz usr/lib/ || exit 1

cp sunec.jar artifacts/sunec-${artifactBaseName}.jar
cp sunpkcs11.jar artifacts/sunpkcs11-${artifactBaseName}.jar

# Make a copy that we can attach as a build artifact
#cp artifacts/openjdk-patch.tar.xz artifacts/${artifactBaseName}-patch.tar.xz || exit 1

ls -l artifacts/*

zip -r artifacts-$artifactBaseName.zip artifacts/

