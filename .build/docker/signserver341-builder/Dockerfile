FROM --platform=linux/amd64 centos:6.10

WORKDIR /workspace

COPY centos6-vault.repo /etc/yum.repos.d/CentOS-Base.repo
COPY jpackage.repo /etc/yum.repos.d/jpackage.repo
COPY jpackage.gpgkey /etc/pki/rpm-gpg/RPM-GPG-KEY-jpackage

RUN yum -y install ca-certificates && \
    yum -y install epel-release yum-priorities && \
    yum -y install \
        rpm-build \
        redhat-rpm-config \
        tar \
        gzip \
        git \
        which \
        java-1.6.0-openjdk \
        java-1.6.0-openjdk-devel \
        jpackage-utils && \
    yum -y --enablerepo=jpackage install \
        ant \
        ant-javamail \
        glassfish-javamail \
        jbossas-5.1.0-30.jpp6 && \
    /usr/bin/build-jar-repository -p /usr/share/ant/lib ant && \
    yum clean all

ENV JBOSS_HOME=/var/lib/jbossas
ENV APPSRV_HOME=/var/lib/jbossas
ENV ANT_HOME=/usr/share/ant
ENV SIGNSERVER_HOME=/workspace/signserver
ENV ANT_OPTS="-Xmx512m -XX:MaxPermSize=256m -Dhealthcheck.authorizedips=ANY"

CMD ["/bin/bash"]
