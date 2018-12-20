# Use latest jboss/base-jdk:7 image as the base
FROM jboss/base-jdk:7

USER root

# Install Ant
RUN yum update -y \
    && yum install -y ant \ 
    && yum install -y ant-junit \
    && yum install -y strace \
    && yum install -y openssl \
    && yum clean all

# Set the WILDFLY_VERSION env variable
ENV JBOSS_EAP_VERSION 6.3.0
ENV JBOSS_EAP_FOLDER jboss-eap-6.3
ENV JBOSS_EAP_SHA256 7f4e6d63196edc3cf15240b693391b9c0be474cda2194ba7575be31881f1a3d5
ENV JBOSS_HOME /opt/jboss/$JBOSS_EAP_FOLDER
ENV APPSRV_HOME $JBOSS_HOME
# TODO: parameterize this
ENV JAVA_HOME /usr/lib/jvm/java-1.7.0-openjdk-1.7.0.201-2.6.16.1.el7_6.x86_64/jre

# Make Java keystore read/write for all users
RUN chmod 666 $JAVA_HOME/lib/security/cacerts

WORKDIR /root

COPY binaries/jboss-eap-$JBOSS_EAP_VERSION.zip ./

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

USER jboss

# Expose the ports we're interested in
EXPOSE 8080
EXPOSE 4447

# Set the default command to run on boot
# This will boot WildFly in the standalone mode and bind to all interface
CMD ["$JBOSS_HOME/bin/standalone.sh", "-b", "0.0.0.0"]
