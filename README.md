# 寒棠 3.0

## 介绍

寒棠 3.0 包括寒棠 Daily、寒棠 Minute 两部分构成。顾名思义，Daily 每天运行一次，而 Minute 每分钟都在运行。二者的职责也有所不同。

|       | 寒棠 Daily                         | 寒棠 Minute      |
|-------|----------------------------------|----------------|
| 主要功能  | (1) 每日增量获取静态数据<br>(2) 每日全量获取动态数据 | 每分钟获取动态数据      |
| 时间粒度  | 每天                               | 每分钟            |
| 调度方式  | 操作系统 (OS)                        | Java虚拟机 (JVM)  |
| 写 入 表 | `video_daily`                    | `video_minute` |
| 应用前身  | 天钿 Daily (模仿)                    | 寒棠 2.0         |
| 下游应用  | 统计月报、洛榜、年榜等                      | 殿堂、传说计时        |


## 寒棠 Daily 逻辑

寒棠 Daily 由两部分构成，增量获取新视频，和全量获取所有视频的数据。

有操作系统自带的定时功能来触发本程序运行。两部分功能有先后顺序，并且一定程度上错开时间。

### 增量获取新视频（静态数据）

通过Bilibili的搜索API，填写参数使其按时间倒序排序，对诸如“洛天依”“中文VOCALOID”关键词进行搜索。搜索到的视频投稿时间超过上次搜索的时间点就截止。

搜索结果先保存在内存中，进行去重后写入 PostgreSQL 视频信息表。

如此获得到的数据称为**静态数据**，例如视频的投稿时间`pubdate`、UP主`mid`、标题`title`等信息。

### 全量获取数据（动态数据）

遍历全量视频（预测在30万数量级），多线程并发调用Bilibili API，得到结果。分批次落入 PostgreSQL 全量信息表中。

这里的数据是动态数据，例如播放量`view`、收藏量`favorite`等，基本都是整数类型的。每日都有大量数据落表。

## 寒棠 Minute 逻辑

### 优先度

寒棠 Minute 根据视频的优先度，进行分层级获取数据。优先度最高的被监测视频，将会每 1 分钟记录一条数据。
所谓优先度，就是记录的时间间隔（周期），优先度为 $p$ 的视频，将会每 $p$ 分钟记录一条数据。
故而优先度 1 是最高优先度。优先度由 PostgreSQL 的 `video_collection_state.priority` 给出。

优先度 `1..720` 表示分钟任务每隔多少分钟采集一次，`0` 表示仅每日任务采集，`-2` 表示仅每周日的每日任务采集。

### 优先度变更

#### 1. 用户手动设置的优先度

在另一个项目`寒棠 web`当中，允许用户将某个视频设置为观测对象，这种情况下，将视频的优先度设置为`1`。

#### 2. 临近殿堂传说

尚在设计中。

#### 3. 常驻每小时监测

所有的传说曲都常驻每小时监测。所有 500 万播放量以上的暂时也每分钟监测。

### 双“生产者-消费者”模型

寒棠 Minute 构成了两个“生产者-消费者”模型，即两个队列。

从数据库读取本轮查询涉及的视频列表，“生产”放入第一个队列。

相应的多个线程“消费”第一个队列的视频 AV 号，发起 Bilibili API 请求，相应结果“生产”放入第二个队列。

相应的多个线程“消费”第二个队列的响应结果，批量存入数据表。

## B站API

### 验权

1. 获取实时口令 `img_key`、`sub_key`
1. 打乱重排实时口令获得 `mixin_key`
1. 计算签名（即 `w_rid`）
1. 向原始请求参数中添加 `w_rid`、`wts` 字段

详细文档请参阅 [WBI 签名](https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md)

## 数据表设计

### 维度表

用户信息写入 PostgreSQL 的 `discovered_users`。分区信息直接保存在 `video_static.type_id` 中。虚拟歌手关系不再由本项目写入。

### 事实表

(1) 视频静态信息 

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

这里的外键被注释掉，因为并不需要事实上的外键，只需要逻辑上的外键。我们不对外键进行严格检查。

(2) 视频动态数据

这里不存放用户ID等是静态数据。一个视频一旦投稿，其UP主不会改变。

该表由寒棠 Daily 每天写入。

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

(3) 视频分钟数据

和视频动态数据的表格区别在于，这里的第一个字段是记录的 UNIX 时间戳，而不是日期。

每一个视频每天在视频动态数据表里只有一条记录，在视频逐分钟数据里，最快每分钟就有一条记录。

这个表设计的索引 `idx_aid_view` (`aid`, `view`) 是为了更快地查询给定视频在何时达成殿堂/传说的。

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


## 环境

### Java

本项目使用JDK 21版本进行编写、编译、打包。故而推荐使用JRE 21环境运行。具体地，在服务器和景育的开发环境中，使用`corrette-21.0.4`版本。

```txt
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Corretto-21.0.4.7.1 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Corretto-21.0.4.7.1 (build 21.0.4+7-LTS, mixed mode, sharing)
```

### PostgreSQL

本项目使用 PostgreSQL，表结构以 `hantang-dynamic` 的 schema 为准。

## 配置

### 配置项

本项目有两个配置项，分别是`config.properties`和`config.secret.properties`。
前者保存了诸如发起请求时的`static.page_size`等信息，推荐设置如下（关键词根据需求调整）：
```properties
# today static data job
static.time_range = 86400
static.page_size = 50
static.keywords = 洛天依,言和,乐正绫,乐正龙牙,徵羽摩柯,墨清弦,星尘,海伊,赤羽,诗岸,苍穹,永夜,心华,中文VOCALOID

# today dynamic data job
dynamic.group_size = 50
```

后者则是秘密信息，如数据库连接的账号、密码等。模板如下：
```properties
postgres.host=${your domain}
postgres.port=5432
postgres.database=hantang
postgres.user=${your user account}
postgres.password=${your user password}
# optional, when video_collection_state is not on the default search_path
postgres.schema=hantang_dynamic
```

上述两个配置文件应当放置在运行Java程序的工作目录中。

### 日志
本项目的日志使用`Log4j2`依赖，默认生成在`./logs`目录下。不需要手动创建该目录，因为会自动创建。

## CI/CD 自动化

本项目配置了 GitHub Actions 进行自动化构建和发布。

## 编译与启动

在确保Maven依赖、JDK版本等信息之后，可以编译或启动本项目。

### 在IntelliJ IDEA中Run或Debug

直接在`./src/main/java/Main`的函数`Main`处Run或Debug。

### 典型编译打包

在IntelliJ IDEA的`Project Structure`中设置`Artifacts`，
添加`JAR`选择`From modules with dependencie`，入口选择`./src/main/java/Main`的函数`Main`。
建议选择将依赖全部打包进JAR中，可以得到**一个**输出文件，重命名为`app.jar`。便可以按照下文方法进行典型启动。

### 典型启动

(1) 确保 PostgreSQL 中已初始化 `hantang-dynamic` 的表结构。

(2) 典型的文件目录如下：

```
./app.jar
./config.properties
./config.secret.properties 
./logs
    ./logs/app.log
```

(3) 可以考虑如下Shell指令启动。如果没有配置默认的java，需要指明java路径。
```bash
java -jar ./app.jar
```

(4) 默认的启动参数为`full`，即先获取近1日投稿的视频静态信息（通过搜索），再获取全量的视频动态数据。
如果只需要静态信息或只需要动态数据，可以选择启动参数`static`或`dynamic`。例如
```bash
java -jar ./app.jar static
```


