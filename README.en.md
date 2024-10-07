# Hantang Daily

## Introduction

Daily retrieval of video information for Bilibili virtual singers (e.g., Luo Tianyi) and related keywords. Each day, it fetches newly uploaded videos incrementally and traverses all videos comprehensively to gather data.

## Features

This software consists of two parts: incrementally retrieving new videos and comprehensively collecting data on all videos.

It utilizes the operating system's built-in scheduling feature to trigger the execution of this program. The two functional parts have a sequential order and are staggered in execution to some extent.

### Incremental Retrieval of New Videos (Static Data)

Using Bilibili search API, parameters are filled in to sort results in reverse chronological order, searching for keywords such as "Luo Tianyi" and "Chinese VOCALOID." The search stops when the submission time of the videos found exceeds the last search timestamp.

The search results are first stored in memory, deduplicated, and then written into the MySQL video information table.

The data obtained in this manner is referred to as **static data**, such as the video submission time `pubdate`, the uploader's ID `mid`, title `title`, and so on.

### Comprehensive Data Retrieval (Dynamic Data)

Traverse all videos (estimated to be around 300,000 in total), using multithreaded concurrent calls to the Bilibili API to obtain results. The results are stored in batches into the MySQL comprehensive information table.

This data is dynamic, including metrics such as views `view`, favorites `favorite`, etc., which are mostly of integer type. A large amount of data is written to the table daily.

## Bilibili API

### Authentication

1. Obtain real-time tokens `img_key` and `sub_key`.
1. Shuffle and rearrange the real-time tokens to obtain `mixin_key`.
1. Calculate the signature (i.e., `w_rid`).
1. Add the `w_rid` and `wts` fields to the original request parameters.

For detailed documentation, please refer to [WBI Signature](https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md).

## Data Table Design

### Dimension Tables

Dimension tables include partition information tables and user information tables.

```mysql-sql
CREATE TABLE IF NOT EXISTS dim_type (
    type_id INT PRIMARY KEY COMMENT 'Partition ID',
    name VARCHAR(255) NOT NULL COMMENT 'Partition Name'
) COMMENT = 'Partition Dimension Table';

CREATE TABLE IF NOT EXISTS dim_user (
    user_id BIGINT PRIMARY KEY COMMENT 'User ID',
    name VARCHAR(255) NOT NULL COMMENT 'Username',
    face VARCHAR(255) COMMENT 'User Avatar URL'
) COMMENT = 'User Dimension Table';
```
### Fact Tables

(1) Video Static Information

```mysql-sql
CREATE TABLE IF NOT EXISTS video_static (
    aid BIGINT PRIMARY KEY COMMENT '视频的 AV 号',
    bvid VARCHAR(50) NOT NULL COMMENT '视频的 BV 号',
    pubdate INT NOT NULL COMMENT '投稿时间',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    description TEXT COMMENT '简介',
    tag TEXT COMMENT '标签',
    pic VARCHAR(255) COMMENT '封面 URL',
    type_id INT COMMENT '分区 ID',
    user_id BIGINT COMMENT 'UP主 ID',
    KEY `idx_bvid` (bvid),
    KEY `idx_user_id` (user_id)
    -- FOREIGN KEY (type_id) REFERENCES type(id) ON DELETE SET NULL,
    -- FOREIGN KEY (user_mid) REFERENCES user(mid) ON DELETE SET NULL
) COMMENT = '视频静态信息';
```

The foreign keys here are commented out because strict foreign key checks are not needed; only logical foreign keys are required.

(2) Video Dynamic Data

User ID is not stored here because it is static data. Once a video is uploaded, its uploader will not change.


```mysql-sql
CREATE TABLE IF NOT EXISTS video_dynamic (
    `record_date` DATE NOT NULL COMMENT '记录日期', 
    `aid` BIGINT NOT NULL COMMENT '视频的 AV 号',
    `bvid` VARCHAR(255) NOT NULL COMMENT '视频的 BV 号',
    `coin` INT NOT NULL COMMENT '硬币',
    `favorite` INT NOT NULL COMMENT '收藏',
    `danmaku` INT NOT NULL COMMENT '弹幕',
    `view` INT NOT NULL COMMENT '播放',
    `reply` INT NOT NULL COMMENT '评论',
    `share` INT NOT NULL COMMENT '分享',
    `like` INT NOT NULL COMMENT '点赞',
    PRIMARY KEY (`record_date`, `aid`),
    INDEX `idx_aid` (`aid`),
    INDEX `idx_bvid` (`bvid`),
    INDEX `idx_view` (`view`),
    INDEX `idx_record_date` (`record_date`)
) COMMENT = '视频动态数据';
```

## Environment

### Java

This project is written, compiled, and packaged using JDK 21. Therefore, it is recommended to run it in a JRE 21 environment. Specifically, in the server and Jingyu's development environment, the `corretto-21.0.4` version is used.

```txt
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Corretto-21.0.4.7.1 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Corretto-21.0.4.7.1 (build 21.0.4+7-LTS, mixed mode, sharing)
```

### MySQL

This project uses MySQL 8.0, and the created database is named hantang. The MySQL details are as follows:

```txt
Ver 8.0.31 for Win64 on x86_64 (MySQL Community Server - GPL)
```

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
db.url_local=jdbc:mysql://${your domain}:3306/hantang
db.user_local=${your user account}
db.password_local=${your user password}
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

(1) Ensure that the four data tables mentioned earlier are created in the MySQL database.

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