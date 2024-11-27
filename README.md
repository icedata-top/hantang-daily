# 寒棠 3.0

## 介绍

寒棠 3.0 包括寒棠 Daily、寒棠 Minute 两部分构成。顾名思义，Daily 每天运行一次，而 Minute 每分钟都在运行。二者的职责也有所不同。

|       | 寒棠 Daily                         | 寒棠 Minute      |
|-------|----------------------------------|----------------|
| 主要功能  | (1) 每日增量获取静态数据<br>(2) 每日全量获取动态数据 | 每分钟获取动态数据      |
| 时间粒度  | 每天                               | 每分钟            |
| 调度方式  | 操作系统 (OS)                        | Java虚拟机 (JVM)  |
| 写 入 表 | `video_dynamic`                  | `video_minute` |
| 应用前身  | 天钿 Daily (模仿)                    | 寒棠 2.0         |
| 下游应用  | 统计月报、洛榜、年榜等                      | 殿堂、传说计时        |


## 寒棠 Daily 逻辑

寒棠 Daily 由两部分构成，增量获取新视频，和全量获取所有视频的数据。

有操作系统自带的定时功能来触发本程序运行。两部分功能有先后顺序，并且一定程度上错开时间。

### 增量获取新视频（静态数据）

通过Bilibili的搜索API，填写参数使其按时间倒序排序，对诸如“洛天依”“中文VOCALOID”关键词进行搜索。搜索到的视频投稿时间超过上次搜索的时间点就截止。

搜索结果先保存在内存中，进行去重后写入MySQL视频信息表。

如此获得到的数据称为**静态数据**，例如视频的投稿时间`pubdate`、UP主`mid`、标题`title`等信息。

### 全量获取数据（动态数据）

遍历全量视频（预测在30万数量级），多线程并发调用Bilibili API，得到结果。分批次落入MySQL全量信息表中。

这里的数据是动态数据，例如播放量`view`、收藏量`favorite`等，基本都是整数类型的。每日都有大量数据落表。

## 寒棠 Minute 逻辑

### 优先度

寒棠 Minute 根据视频的优先度，进行分层级获取数据。优先度最高的被监测视频，将会每 1 分钟记录一条数据。
所谓优先度，就是记录的时间间隔（周期），优先度为 $p$ 的视频，将会每 $p$ 分钟记录一条数据。
故而优先度 1 是最高优先度。优先度将会在视频静态信息表中，由字段`priority`给出。

优先度仅为 1、15、60 三个数值，为 null 的视为最低优先度。

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

由数据源产生的维度表包含**分区信息表**和**用户信息表**。

```mysql-sql
CREATE TABLE IF NOT EXISTS dim_type (
    type_id INT PRIMARY KEY COMMENT '分区 ID',
    name VARCHAR(255) NOT NULL COMMENT '分区名称'
) COMMENT = '分区_维度表';

CREATE TABLE IF NOT EXISTS dim_user (
    user_id BIGINT PRIMARY KEY COMMENT '用户 ID',
    name VARCHAR(255) NOT NULL COMMENT '用户名',
    face VARCHAR(255) COMMENT '用户头像 URL'
) COMMENT = '用户_维度表';
```

另手动创建虚拟歌手维度表，该表仅手动维护。
```mysql-sql
CREATE TABLE IF NOT EXISTS dim_vocal (
    vocal_id INT PRIMARY KEY COMMENT '虚拟歌手 ID',
    `name` VARCHAR(255) NOT NULL COMMENT '虚拟歌手名称',
    `group` VARCHAR(255) NOT NULL COMMENT '虚拟歌手组团'
) COMMENT = '虚拟歌手_维度表';
```

### 事实表

(1) 视频静态信息 

```mysql-sql
CREATE TABLE IF NOT EXISTS video_static (
    `aid` bigint NOT NULL COMMENT '视频的 AV 号',
    `bvid` varchar(50) NOT NULL COMMENT '视频的 BV 号',
    `pubdate` int NOT NULL COMMENT '投稿时间',
    `title` varchar(255) NOT NULL COMMENT '标题',
    `description` text COMMENT '简介',
    `tag` text COMMENT '标签',
    `pic` varchar(255) DEFAULT NULL COMMENT '封面 URL',
    `type_id` int DEFAULT NULL COMMENT '分区 ID',
    `user_id` bigint DEFAULT NULL COMMENT 'UP主 ID',
    `priority` int DEFAULT NULL COMMENT '优先级，获取数据的时间间隔（单位：分钟）',
    PRIMARY KEY (`aid`),
    KEY `idx_bvid` (bvid),
    KEY `idx_user_id` (user_id),
    KEY `idx_priority` (`priority`),
    KEY `idx_pubdate` (`pubdate`)
    -- FOREIGN KEY (type_id) REFERENCES type(id) ON DELETE SET NULL,
    -- FOREIGN KEY (user_mid) REFERENCES user(mid) ON DELETE SET NULL
) COMMENT = '视频静态信息';
```

这里的外键被注释掉，因为并不需要事实上的外键，只需要逻辑上的外键。我们不对外键进行严格检查。

(2) 视频动态数据

这里不存放用户ID等是静态数据。一个视频一旦投稿，其UP主不会改变。

该表由寒棠 Daily 每天写入。

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

(3) 视频分钟数据

和视频动态数据的表格区别在于，这里的第一个字段是记录的 UNIX 时间戳，而不是日期。

每一个视频每天在视频动态数据表里只有一条记录，在视频逐分钟数据里，最快每分钟就有一条记录。

这个表设计的索引 `idx_aid_view` (`aid`, `view`) 是为了更快地查询给定视频在何时达成殿堂/传说的。

```mysql-sql
CREATE TABLE `video_minute` (
    `time` int NOT NULL COMMENT '记录时间戳',
    `aid` bigint NOT NULL COMMENT '视频的 AV 号',
    `bvid` varchar(255) NOT NULL COMMENT '视频的 BV 号',
    `coin` int NOT NULL COMMENT '硬币',
    `favorite` int NOT NULL COMMENT '收藏',
    `danmaku` int NOT NULL COMMENT '弹幕',
    `view` int NOT NULL COMMENT '播放',
    `reply` int NOT NULL COMMENT '评论',
    `share` int NOT NULL COMMENT '分享',
    `like` int NOT NULL COMMENT '点赞',
    PRIMARY KEY (`time`, `aid`),
    KEY `idx_aid` (`aid`),
    KEY `idx_bvid` (`bvid`),
    KEY `idx_aid_view` (`aid`, `view`)
) COMMENT = '视频逐分钟数据';
```


### OLAP表

以上的维度表和事实表为底表，但是每次查询时，并不是总是得经由底表，这样会导致查询缓慢。因此，需要适当地对数据进行聚合，得到OLAP表（相当于二级结论）。

(1) 歌曲与虚拟歌手的关系表

多对多映射关系，因为1位虚拟歌手（如洛天依）可以唱多首歌，并且1首歌（如《普通DISCO》）可以被多位虚拟歌手演唱。

利用此表，可以查询某个歌手/组团有哪些作品。

```mysql-sql
CREATE TABLE `hantang`.`olap_rel_video_vocal` (
    `aid` bigint NOT NULL COMMENT '视频的 AV 号',
    `vocal_id` int NOT NULL COMMENT '虚拟歌手 ID',
    PRIMARY KEY (`aid`, `vocal_id`),
    KEY `idx_vocal_id` (`vocal_id`)
) COMMENT = '歌曲与虚拟歌手的关系';
```

(2) 三个维度的每日汇总信息

三个维度指的是：虚拟歌手、组团、总计。
这里的汇总信息并不是“当天”的，而是“截止当天”的。所以要算出当天的数据，需要再次进行差分。

例如，使用筛选条件`WHERE d = '2024-10-11' AND cube_id = 1 AND dimens = '洛天依'`，查询到的投稿数指从有记录以来截止2024年10月11日的洛天依的投稿数，并非2024年10月11日当天洛天依的投稿数。

```mysql-sql
CREATE TABLE IF NOT EXISTS olap_aggre (
    `d` DATE NOT NULL COMMENT '日期',
    `cube_id` INT NOT NULL COMMENT '立方体 ID 1虚拟歌手 2组团 3总计',
    `dimens` VARCHAR(255) NOT NULL COMMENT '维度',
    `cnt` INT NOT NULL COMMENT '累计投稿数',
    `view` BIGINT NOT NULL COMMENT '累计播放',
    `favorite` BIGINT NOT NULL COMMENT '累计收藏'
) COMMENT = '每日汇总信息';
```


## 环境

### Java

本项目使用JDK 21版本进行编写、编译、打包。故而推荐使用JRE 21环境运行。具体地，在服务器和景育的开发环境中，使用`corrette-21.0.4`版本。

```txt
openjdk version "21.0.4" 2024-07-16 LTS
OpenJDK Runtime Environment Corretto-21.0.4.7.1 (build 21.0.4+7-LTS)
OpenJDK 64-Bit Server VM Corretto-21.0.4.7.1 (build 21.0.4+7-LTS, mixed mode, sharing)
```

### MySQL

本项目使用MySQL 8.0，创建的数据库名为`hantang`。MySQL具体如下
```txt
Ver 8.0.31 for Win64 on x86_64 (MySQL Community Server - GPL)
```

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
db.url_local=jdbc:mysql://${your domain}:3306/hantang
db.user_local=${your user account}
db.password_local=${your user password}
```

上述两个配置文件应当放置在运行Java程序的工作目录中。

### 日志
本项目的日志使用`Log4j2`依赖，默认生成在`./logs`目录下。不需要手动创建该目录，因为会自动创建。

## 编译与启动

在确保Maven依赖、JDK版本等信息之后，可以编译或启动本项目。

### 在IntelliJ IDEA中Run或Debug

直接在`./src/main/java/Main`的函数`Main`处Run或Debug。

### 典型编译打包

在IntelliJ IDEA的`Project Structure`中设置`Artifacts`，
添加`JAR`选择`From modules with dependencie`，入口选择`./src/main/java/Main`的函数`Main`。
建议选择将依赖全部打包进JAR中，可以得到**一个**输出文件，重命名为`app.jar`。便可以按照下文方法进行典型启动。

### 典型启动

(1) 确保在MySQL数据库中创建好了前文所说的四张数据表。

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


