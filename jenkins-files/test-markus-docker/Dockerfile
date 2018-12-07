FROM openjdk:8u171-jdk-stretch

# Install Maven
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y maven \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*  


# Install Apache Ant
ENV ANT_SHA512 873062cf789e1572ec4ef3cdbb15c0d3a57e5a0794068591565367d4cec15b9f9a24f59734a85170d61b23eb9288d6bafd35a9ae2b015b09c7a175e344cb4e1c
ENV ANT_HOME /opt/ant/apache-ant-1.9.13

RUN cd $HOME \
    && curl -O https://archive.apache.org/dist/ant/binaries/apache-ant-1.9.13-bin.tar.gz \
    && sha512sum apache-ant-1.9.13-bin.tar.gz | grep $ANT_SHA512 \
    && tar xf apache-ant-1.9.13-bin.tar.gz \
    && mkdir -p /opt/ant/ \
    && mv $HOME/apache-ant-1.9.13 $ANT_HOME \
    && rm apache-ant-1.9.13-bin.tar.gz \
    && chmod -R g+rw ${ANT_HOME}

ENV ANT_OPTS="-Xms256M -Xmx512M"
ENV PATH="${PATH}:${HOME}/bin:${ANT_HOME}/bin"
