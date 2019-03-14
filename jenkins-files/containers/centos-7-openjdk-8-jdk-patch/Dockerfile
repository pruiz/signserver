FROM centos:7
# Consider building this with "--disable-content-trust=false --pull" e.g.
#   docker build --disable-content-trust=false --pull -t pk-test/patchjdk . && docker run -it --rm -v /tmp:/build/artifacts pk-test/patchjdk

RUN \
    yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel && \
    yum install -y java-1.8.0-openjdk-src && \
    yum install -y make unzip patch which && \
    yum clean all && \
    rm -rf /var/cache/yum

WORKDIR /build/

#ADD run.sh /build/

RUN \
    chmod 777 /build/

RUN groupadd -g 1000 user
RUN useradd --uid 1000 -g 1000 user
USER user


