package dos;

/**
 * B站分区，例如typeId=31的分区是“翻唱”区。
 */
public record TypeDO(
        int typeId,
        String typeName
) {
}
