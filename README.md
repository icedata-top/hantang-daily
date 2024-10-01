# 寒棠Daily

## 介绍

每日获取Bilibili虚拟歌手（例：洛天依）等关键词的视频信息。每日（增量）获取新投稿视频，并且遍历全部视频（全量）获取数据。

## 功能

本软件由两部分构成，增量获取新视频，和全量获取所有视频的数据。

有操作系统自带的定时功能来触发本程序运行。两部分功能有先后顺序，并且一定程度上错开时间。

### 增量获取新视频

通过Bilibili的搜索API，填写参数使其按时间倒序排序，对诸如“洛天依”“中文VOCALOID”关键词进行搜索。搜索到的视频投稿时间超过上次搜索的时间点就截止。

搜索结果先保存在内存中，进行去重后写入MySQL视频信息表。

### 全量获取数据

遍历全量视频（预测在30万数量级），多线程并发调用Bilibili API，得到结果。分批次落入MySQL全量信息表中。

## 数据表设计

todo

## 第三方依赖

todo

