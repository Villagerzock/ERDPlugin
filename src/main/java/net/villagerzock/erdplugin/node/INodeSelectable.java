package net.villagerzock.erdplugin.node;

public interface INodeSelectable {
    default boolean isMinimapSelected(INodeSelectable selected){
        return isSelected(selected);
    }
    default boolean isSelected(INodeSelectable selected){
        return this.equals(selected);
    }

    static boolean isMinimapSelected(INodeSelectable selected, INodeSelectable other){
        if (selected == null){
            return false;
        }
        return selected.isMinimapSelected(other);
    }

    static boolean isSelected(INodeSelectable selected, INodeSelectable other){
        if (selected == null){
            return false;
        }
        return selected.isSelected(other);
    }

    void mergeInto(MultiSelection multiSelection);
    void moveBy(double dx, double dy);
}
