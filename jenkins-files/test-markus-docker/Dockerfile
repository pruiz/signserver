#FROM alpine:3.5

#FROM maven:3.5.4-jdk-8-alpine
FROM openjdk:8u181-jdk-stretch

# Install maven
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive \
    apt-get install -y maven \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*  

#ARG MAVEN_VERSION=3.6.0
#ARG USER_HOME_DIR="/root"
#ARG SHA=fae9c12b570c3ba18116a4e26ea524b29f7279c17cbaadc3326ca72927368924d9131d11b9e851b8dc9162228b6fdea955446be41207a5cfc61283dd8a561d2f
#ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries
#
#RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
#  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
#  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
#  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
#  && rm -f /tmp/apache-maven.tar.gz \
#  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

#Create Ant Dir
RUN mkdir -p /opt/ant/

#Download Ant 1.9.8
ENV ANT_SHA1 ca31bd42c27f7e63cccb219ee59930fdc943ca82
ENV ANT_HOME /opt/ant/apache-ant-1.9.8

RUN cd $HOME \
    && curl -O http://archive.apache.org/dist/ant/binaries/apache-ant-1.9.8-bin.tar.gz \
    && sha1sum apache-ant-1.9.8-bin.tar.gz | grep $ANT_SHA1 \
    && tar xf apache-ant-1.9.8-bin.tar.gz \
    && mv $HOME/apache-ant-1.9.8 $ANT_HOME \
    && rm apache-ant-1.9.8-bin.tar.gz \
    && chmod -R g+rw ${ANT_HOME}
    

#Setting Ant Params
ENV ANT_OPTS="-Xms256M -Xmx512M"

#Updating Path
ENV PATH="${PATH}:${HOME}/bin:${ANT_HOME}/bin"

# copy ejbca conf to /opt in the container for runtime-usage
#ADD conf /opt/conf

#ENV MAVEN_HOME /usr/share/maven
#ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"
#
#COPY mvn-entrypoint.sh /usr/local/bin/mvn-entrypoint.sh
#COPY settings-docker.xml /usr/share/maven/ref/
#
#ENTRYPOINT ["/usr/local/bin/mvn-entrypoint.sh"]
#CMD ["mvn"]
