FROM centos:7

RUN yum update -y \
    && yum install -y java-1.8.0-openjdk unzip wget tar which expect perl \
    && yum clean all -y \
    && rm -rf /var/cache/yum

ARG PROJ_VERS
ARG IMG_NAME

ADD target/emissary-${PROJ_VERS}-dist.tar.gz /opt/

RUN ln -s /opt/emissary-${PROJ_VERS} /opt/emissary

WORKDIR /opt/emissary

EXPOSE 8001

ENTRYPOINT ["./emissary"]

CMD ["server", "-a", "2", "-p", "8001"]

LABEL version=${PROJ_VERS} \
      run="docker run -it --rm -v /local/data:/opt/emissary/target/data --name emissary ${IMG_NAME}"
