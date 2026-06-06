# Hantang Daily

## Introduction

Daily retrieval of video information for Bilibili virtual singers (e.g., Luo Tianyi) and related keywords. Each day, it fetches newly uploaded videos incrementally and traverses all videos comprehensively to gather data.

## Features

This software consists of two parts: incrementally retrieving new videos and comprehensively collecting data on all videos.

It utilizes the operating system's built-in scheduling feature to trigger the execution of this program. The two functional parts have a sequential order and are staggered in execution to some extent.

### Incremental Retrieval of New Videos (Static Data)

Using Bilibili search API, parameters are filled in to sort results in reverse chronological order, searching for keywords such as "Luo Tianyi" and "Chinese VOCALOID." The search stops when the submission time of the videos found exceeds the last search timestamp.

The search results are first stored in memory, deduplicated, and then written into the PostgreSQL video information table.

The data obtained in this manner is referred to as **static data**, such as the video submission time `pubdate`, the uploader's ID `mid`, title `title`, and so on.

### Comprehensive Data Retrieval (Dynamic Data)

Traverse all videos (estimated to be around 300,000 in total), using multithreaded concurrent calls to the Bilibili API to obtain results. The results are stored in batches into PostgreSQL.

This data is dynamic, including metrics such as views `view`, favorites `favorite`, etc., which are mostly of integer type. A large amount of data is written to the table daily.

The collection list and minute collection cadence are read from PostgreSQL `video_collection_state`. Priority `1..720` means minute collection should run every N minutes, `0` means daily collection only, and `-2` means Sunday-only daily collection.

## Bilibili API

### Authentication

1. Obtain real-time tokens `img_key` and `sub_key`.
1. Shuffle and rearrange the real-time tokens to obtain `mixin_key`.
1. Calculate the signature (i.e., `w_rid`).
1. Add the `w_rid` and `wts` fields to the original request parameters.

For detailed documentation, please refer to [WBI Signature](https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md).

## Data Table Design

### Dimension Tables

User information is written to PostgreSQL `discovered_users`. Partition data is stored directly in `video_static.type_id`. Vocal relationships are no longer written by this project.

### Fact Tables

(1) Video Static Information

```sql
CREATE TABLE IF NOT EXISTS video_static (
    aid         bigint       PRIMARY KEY,
    bvid        varchar(50)  NOT NULL,
    pubdate     timestamptz  NOT NULL,
    title       varchar(255) NOT NULL,
    description text,
    tag         text,
    pic         varchar(255),
    type_id     integer,
    user_id     bigint,
    priority    integer,
    updated_at  timestamptz  DEFAULT now()
);
```

The foreign keys here are commented out because strict foreign key checks are not needed; only logical foreign keys are required.

(2) Video Dynamic Data

User ID is not stored here because it is static data. Once a video is uploaded, its uploader will not change.


```sql
CREATE TABLE IF NOT EXISTS video_daily (
    record_date  date     NOT NULL,
    aid          bigint   NOT NULL,
    coin         integer,
    favorite     integer,
    danmaku      integer,
    "view"       integer,
    reply        integer,
    share        integer,
    "like"       integer
);
```

(3) Video Minute Data

```sql
CREATE TABLE IF NOT EXISTS video_minute (
    "time"    timestamptz  NOT NULL,
    aid       bigint       NOT NULL,
    coin      integer,
    favorite  integer,
    danmaku   integer,
    "view"    integer,
    reply     integer,
    share     integer,
    "like"    integer
);
```

## Environment

### Java

This project is written, compiled, and packaged using JDK 21. Therefore, it is recommended to run it in a JRE 21 environment. Specifically, in the server and Jingyu's development environment, the `corretto-21.0.4` version is used.

```txt
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Corretto-21.0.4.7.1 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Corretto-21.0.4.7.1 (build 21.0.4+7-LTS, mixed mode, sharing)
```

### PostgreSQL

This project uses PostgreSQL. The table schema follows `hantang-dynamic`.

## Configuration

### Configuration Items

This project has two configuration files: config.properties and config.secret.properties. The former contains information such as static.page_size for initiating requests, and it is recommended to set it as follows (keywords can be adjusted as needed):

```properties
# today static data job
static.time_range = 86400
static.page_size = 50
static.keywords = 洛天依,言和,乐正绫,乐正龙牙,徵羽摩柯,墨清弦,星尘,海伊,赤羽,诗岸,苍穹,永夜,心华,中文VOCALOID

# today dynamic data job
dynamic.group_size = 50
```

The latter contains secret information such as the database connection account and password. The template is as follows:

```properties
postgres.host=${your domain}
postgres.port=5432
postgres.database=hantang
postgres.user=${your user account}
postgres.password=${your user password}
# optional, when tables are not on the default search_path
postgres.schema=hantang_dynamic
```

The above two configuration files should be placed in the working directory where the Java program is run.

### Logs

This project uses the Log4j2 dependency for logging, and by default, logs are generated in the ./logs directory. There is no need to manually create this directory, as it will be created automatically.

## Compilation and Startup

After ensuring that Maven dependencies, JDK versions, and other information are correct, you can compile or start this project.

### Running or Debugging in IntelliJ IDEA

Run or debug directly at the `Main` function located in `./src/main/java/Main`.

### Typical Compilation and Packaging

In IntelliJ IDEA, set up `Artifacts` in the `Project Structure`.
Add a `JAR` and select `From modules with dependencies`, choosing the `Main` function in `./src/main/java/Main` as the entry point.
It is recommended to package all dependencies into the JAR, resulting in **one** output file, which can be renamed to `app.jar`. This allows for typical startup as described below.

### Typical Startup

(1) Ensure that the `hantang-dynamic` PostgreSQL schema has been initialized.

(2) The typical file directory structure is as follows:

```txt
./app.jar 
./config.properties 
./config.secret.properties 
./logs 
    ./logs/app.log
```


(3) You can consider using the following Shell command to start the application. If the default Java is not configured, you need to specify the Java path.

```bash
java -jar ./app.jar
```

(4) The default startup parameter is `full`, which retrieves the static information of videos uploaded in the last day (via search) and then fetches the comprehensive dynamic data of all videos. If you only need static information or only dynamic data, you can use the startup parameters `static` or `dynamic`. For example:

```bash
java -jar ./app.jar static
```
