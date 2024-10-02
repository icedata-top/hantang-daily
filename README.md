# 寒棠Daily

## 介绍

每日获取Bilibili虚拟歌手（例：洛天依）等关键词的视频信息。每日（增量）获取新投稿视频，并且遍历全部视频（全量）获取数据。

## 功能

本软件由两部分构成，增量获取新视频，和全量获取所有视频的数据。

有操作系统自带的定时功能来触发本程序运行。两部分功能有先后顺序，并且一定程度上错开时间。

### 增量获取新视频（静态数据）

通过Bilibili的搜索API，填写参数使其按时间倒序排序，对诸如“洛天依”“中文VOCALOID”关键词进行搜索。搜索到的视频投稿时间超过上次搜索的时间点就截止。

搜索结果先保存在内存中，进行去重后写入MySQL视频信息表。

如此获得到的数据称为**静态数据**，例如视频的投稿时间`pubdate`、UP主`mid`、标题`title`等信息。

### 全量获取数据（动态数据）

遍历全量视频（预测在30万数量级），多线程并发调用Bilibili API，得到结果。分批次落入MySQL全量信息表中。

这里的数据是动态数据，例如播放量`view`、收藏量`favorite`等，基本都是整数类型的。每日都有大量数据落表。

## B站API

### 验权

1. 获取实时口令 img_key、sub_key
1. 打乱重排实时口令获得 mixin_key
1. 计算签名（即 w_rid）
1. 向原始请求参数中添加 w_rid、wts 字段

详细文档请参阅 [WBI 签名](https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md)

## 数据表设计

### 维度表

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
### 事实表

(1) 视频静态信息 

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

(2) 视频动态数据

这里不存放用户ID，因为用户ID是静态数据。一个视频一旦投稿，其UP主不会改变。

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


## 第三方依赖

todo

