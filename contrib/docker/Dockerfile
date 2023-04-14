FROM centos:7

RUN yum update -y \
    && rpm --import https://yum.corretto.aws/corretto.key \
    && curl -L -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo \
    && yum install -y java-11-amazon-corretto-devel \
    && yum clean all -y \
    && rm -rf /var/cache/yum

ADD target/emissary-*-dist.tar.gz /opt

RUN ls -al /opt && \
    version=`ls /opt | grep emissary- | awk '{ print $1 }'` && \
    echo "Linking /opt/${version} to /opt/emissary" && \
    ln -s /opt/${version} /opt/emissary && \
    mkdir -p /opt/emissary/localoutput && \
    chmod -R a+rw /opt/emissary

ENV PROJECT_BASE=/opt/emissary

WORKDIR /opt/emissary

VOLUME /opt/emissary/localoutput

EXPOSE 8000 8001

ENTRYPOINT ["./emissary"]

CMD ["server", "-a", "2", "-p", "8001"]
