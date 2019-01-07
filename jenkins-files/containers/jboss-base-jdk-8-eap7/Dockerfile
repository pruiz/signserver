# Use latest jboss/base-jdk:8 image as the base
FROM jboss/base-jdk:8

USER root

# Install Ant
RUN yum update -y \
    && yum install -y ant \ 
    && yum install -y ant-junit \
    && yum install -y strace \
    && yum install -y openssl \    
    && yum clean all

# Set the WILDFLY_VERSION env variable
ENV JBOSS_EAP_VERSION 7.0.0
ENV JBOSS_EAP_FOLDER jboss-eap-7.0
ENV JBOSS_EAP_SHA256 7c28c74e475a296b6e3370b5eb2e21f158c31f4b50d56dafadada6483b23340e
ENV JBOSS_HOME /opt/jboss/$JBOSS_EAP_FOLDER
ENV APPSRV_HOME $JBOSS_HOME
# TODO: parameterize this
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.191.b12-1.el7_6.x86_64/jre

# Make Sure the SafeNet emulator will not generate a fixed key
# for a given key size/spec
ENV ET_PTKC_SW_AUTOSEEDRNG true

# Eracom emulator
ENV ERACOM_RPM=ETcpsdk-3.33.00-1.x86_64.rpm
ENV ERACOM_SHA256=9ab17a4380771382984285ae59b0405f32c3436a7d154a82a897132940eacbc0

# Emulator prepared setup
ENV CRYPTOKI_ZIP=cryptoki.zip
ENV CRYPTOKI_SHA256=dad7027b5ff1ea615095a4195d7388b6b0aab590a08138d34e23cb9ecaa99cc1

# P11 test config variables
ENV LIBCRYPTOKI="/opt/ETcpsdk/lib/linux-x86_64/libcryptoki.so"
ENV P11SLOT="2"
ENV P11PIN="foo123"
ENV P11KEY="mykey001"

# Make Java keystore read/write for all users
RUN chmod 666 $JAVA_HOME/lib/security/cacerts

WORKDIR /root

COPY binaries/jboss-eap-$JBOSS_EAP_VERSION.zip ./
COPY binaries/$ERACOM_RPM ./
COPY binaries/$CRYPTOKI_ZIP ./

# Assume /binaries is mounted from the pipeline
# Add the JBoss EAP distribution to /opt, and make jboss the owner of the extracted tar content
# Make sure the distribution is available from a well-known place
RUN cd $HOME \
    && ls \
    && pwd \
    && sha256sum jboss-eap-$JBOSS_EAP_VERSION.zip | grep $JBOSS_EAP_SHA256 \
    && unzip jboss-eap-$JBOSS_EAP_VERSION.zip \
    && mv $JBOSS_EAP_FOLDER /opt/jboss/ \
    && rm jboss-eap-$JBOSS_EAP_VERSION.zip \
    && chown -R jboss:0 ${JBOSS_HOME} \
    && chmod -R a+rw ${JBOSS_HOME}              
# TODO only chmod g+rw but make sure it works to run

# Ensure signals are forwarded to the JVM process correctly for graceful shutdown
ENV LAUNCH_JBOSS_IN_BACKGROUND true

# Fix locale
RUN yum reinstall -q -y glibc-common \
    && localedef -i en_US -f UTF-8 en_US.UTF-8 \
    && echo "LANG=en_US.utf8" >> /etc/locale.conf \
    && yum clean all

# Install HSM emulator
RUN cd $HOME \
    && sha256sum $ERACOM_RPM | grep $ERACOM_SHA256 \
    && rpm -i $ERACOM_RPM \
    && rm $ERACOM_RPM

ENV LANG en_US.UTF-8

# Set LD_LIBRARY_PATH for SafeNet
ENV LD_LIBRARY_PATH /opt/ETcpsdk/lib/linux-x86_64

# Unpack HSM emulator setup (couldn't get running
# ctconf setup commands from scratch working,
# requires interactive input of PIN codes)
RUN cd $HOME \
    && sha256sum $CRYPTOKI_ZIP | grep $CRYPTOKI_SHA256 \
    && mkdir -p /opt/eracom \
    && cp $CRYPTOKI_ZIP /opt/eracom \
    && chmod a+rw /opt/eracom/$CRYPTOKI_ZIP \
    && rm $CRYPTOKI_ZIP \
    && echo "pwd: $HOME" && ls -la \
    && echo "home jboss: " && ls -la /opt/jboss \
    && echo "cryptoki lib dir: " && ls -la /opt/ETcpsdk/lib/linux-x86_64

#RUN echo "cryptoki device version: " && LD_LIBRARY_PATH=/opt/ETcpsdk/lib/linux-x86_64 /opt/ETcpsdk/bin/linux-x86_64/ctcheck -V \
#  && echo "cryptoki device slot count: " && LD_LIBRARY_PATH=/opt/ETcpsdk/lib/linux-x86_64 /opt/ETcpsdk/bin/linux-x86_64/ctcheck -b slotcount \
#  && echo "cryptoki device token objects: " && LD_LIBRARY_PATH=/opt/ETcpsdk/lib/linux-x86_64 /opt/ETcpsdk/bin/linux-x86_64/ctstat -j

USER jboss

# Expose the ports we're interested in
EXPOSE 8080
EXPOSE 4447

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["$JBOSS_HOME/bin/standalone.sh", "-b", "0.0.0.0"]
