FROM openjdk:7-jdk-jessie

# Install Maven
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y maven \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*  

# Install Apache Ant
ENV ANT_SHA512 c8321aa223f70d7e64d3d0274263000cfffb46fbea61488534e26f9f0245d99e9872d0888e35cd3274416392a13f80c748c07750caaeffa5f9cae1220020715f
ENV ANT_HOME /opt/ant/apache-ant-1.9.13

RUN cd $HOME \
    && curl -O https://archive.apache.org/dist/ant/binaries/apache-ant-1.9.13-bin.tar.gz \
    && sha512sum apache-ant-1.9.13-bin.tar.gz | grep $ANT_SHA512 \
    && tar xf apache-ant-1.9.13-bin.tar.gz \
    && mkdir -p /opt/ant/ \
    && mv $HOME/apache-ant-1.9.13 $ANT_HOME \
    && rm apache-ant-1.9.13-bin.tar.gz \
    && chmod -R g+rw ${ANT_HOME}

# Work around for missing crypto in openjdk7
RUN curl -O "https://bouncycastle.org/download/bcprov-jdk15on-156.jar" \
    && mv bcprov-jdk15on-156.jar "${JAVA_HOME}"/jre/lib/ext && \
  sudo perl -pi.bak -e 's/^(security\.provider\.)([0-9]+)/$1.($2+1)/ge' /etc/java-7-openjdk/security/java.security && \
  echo "security.provider.1=org.bouncycastle.jce.provider.BouncyCastleProvider" | sudo tee -a /etc/java-7-openjdk/security/java.security

ENV ANT_OPTS="-Xms256M -Xmx512M"
ENV PATH="${PATH}:${HOME}/bin:${ANT_HOME}/bin"
