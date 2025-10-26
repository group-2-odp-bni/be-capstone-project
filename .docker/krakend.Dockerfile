FROM krakend/builder AS builder

COPY krakend-plugins /src/krakend-plugins
WORKDIR /src/krakend-plugins

RUN go mod tidy && go build -buildmode=plugin -o redis-blacklist.so .

FROM devopsfaith/krakend:2.5

COPY --from=builder /src/krakend-plugins/redis-blacklist.so /etc/krakend/plugins/redis-blacklist.so
