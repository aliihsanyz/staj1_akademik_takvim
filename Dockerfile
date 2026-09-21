# syntax=docker/dockerfile:1
# =====================================================================
#  DINAMIK AKADEMIK TAKVIM SISTEMI - UYGULAMA GORUNTUSU (IMAGE)
#
#  Iki asamali (multi-stage) kurgu:
#    1) derleyici : Maven + JDK 21 ile jar uretir
#    2) calisma   : yalnizca JRE 21 + uretilen jar
#
#  Neden iki asama? Maven, kaynak kodu ve ~300 MB'lik bagimlilik onbellegi
#  nihai goruntude yer almaz; sunucuya giden katman yalnizca calismak icin
#  gerekli olani icerir. Boylece goruntu hem kucuk hem de saldiri yuzeyi dar olur.
# =====================================================================

# --------------------------------------------------------- 1. ASAMA: DERLEME
FROM maven:3.9-eclipse-temurin-21 AS derleyici

WORKDIR /derleme

# ONCE yalnizca pom.xml kopyalanir ve bagimliliklar indirilir.
# Docker katmanlari onbelleklendigi icin, kaynak kod degistiginde bu adim
# TEKRAR CALISMAZ; yalnizca pom.xml degisirse calisir. Her derlemede
# bagimliliklarin yeniden indirilmesini onler.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Testler goruntu uretiminde atlanir: test asamasi CI'da / "mvn test" ile
# ayrica kosar. Goruntu uretimi bir dogrulama adimi degil, paketleme adimidir.
RUN mvn -B -q clean package -DskipTests

# --------------------------------------------------------- 2. ASAMA: CALISMA
# Alpine tabanli JRE: Ubuntu tabanli goruntunun yaklasik yarisi kadar yer kaplar.
# PDF ciktisinin fontu ve armasi jar'in ICINDEN okundugu icin kapta ayrica
# font paketi kurmaya gerek yoktur.
FROM eclipse-temurin:21-jre-alpine AS calisma

# TZ: kap varsayilan olarak UTC calisir. Uygulama "bugun" cizgisini ve kalan
# gun sayisini sunucu saatine gore hesapladigi icin saat dilimi sabitlenir.
ENV TZ=Europe/Istanbul \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

# curl yalnizca HEALTHCHECK icin gereklidir (JRE goruntusunde bulunmaz).
# Uygulama root olarak CALISMAZ: kapta bir acik bulunursa saldirgan dogrudan
# root yetkisi elde edemesin diye ayri bir kullanici acilir.
RUN apk add --no-cache curl \
 && addgroup -S takvim \
 && adduser -S -G takvim -h /home/takvim takvim

WORKDIR /uygulama
COPY --from=derleyici /derleme/target/*.jar uygulama.jar
USER takvim

EXPOSE 8080

# /api/saglik ucu kimlik dogrulamasi istemez; saglik kontrolu icin uygundur.
# start-period: ilk acilista sema kurulumu ve font yuklemesi icin tanınan sure.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/saglik || exit 1

ENTRYPOINT ["java", "-jar", "uygulama.jar"]
