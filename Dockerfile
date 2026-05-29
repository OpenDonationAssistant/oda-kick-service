FROM fedora:44
WORKDIR /app
COPY target/oda-kick-service /app

CMD ["./oda-kick-service"]
