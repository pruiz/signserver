FROM primekey/base-centos7-jre8:latest

# Consider building this with "--disable-content-trust=false --pull" e.g.
#   docker build --disable-content-trust=false --pull -t pk-test/patchjdk . && docker run -it --rm -v /tmp:/build/artifacts pk-test/patchjdk

RUN \
    yum install -y -q java-1.8.0-openjdk-src java-1.8.0-openjdk-devel make unzip patch which && \
    yum clean all && \
    rm -rf /var/cache/yum

WORKDIR /build/

ADD run.sh /build/

RUN \
    chmod 777 /build/

CMD ["/build/run.sh"]

