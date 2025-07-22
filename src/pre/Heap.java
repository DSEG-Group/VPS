package pre;

import java.util.ArrayList;

public class Heap {
    public TreeNode[] Tree;
    private int size;
    private boolean isAllLoad;


    Heap(){
        Tree = new TreeNode[1000000];
        size = 0;
        isAllLoad = false;
    }

    Heap(TreeNode node){
        Tree = new TreeNode[1000000];
        Tree[0] = node;
        size = 1;
        isAllLoad = false;
    }

    public TreeNode pop(){
        synchronized(Tree){
            if(size == 0){
                return null;
            }
            TreeNode node = Tree[0];
            Tree[0] = Tree[size-1];
            size--;
            shiftDown(0); 
            return node;
        }
    }

    public void add(TreeNode node){
        synchronized(Tree){
            Tree[size] = node;
            size++;
            shiftUp(size-1);
        }
        return;
    }

    public void shiftUp(int index){
        int parent;
        if(index == 0){
            return;
        }
        if(index%2 == 1){
            parent = index/2;
        }
        else{
            parent = index/2-1;
        }
        if(Tree[index].getGenerateTime()<Tree[parent].getGenerateTime()){
            swap(index,parent);
            shiftUp(parent);
        }
        else{
            return;
        }
    }



    public void shiftDown(int index){
        int left,right;
        left = ((index+1)*2-1);
        right = ((index+1)*2);
        if(left>=size){
            return;
        }
        if(left<size&&right<size){
            if(Tree[index].getGenerateTime()<Tree[left].getGenerateTime()&&Tree[index].getGenerateTime()<Tree[right].getGenerateTime()){
                return;
            }
            if(Tree[left].getGenerateTime()<Tree[right].getGenerateTime()){
                if(Tree[left].getGenerateTime()<Tree[index].getGenerateTime()){
                    swap(left,index);
                    shiftDown(left);
                }
            }else{
                if(Tree[right].getGenerateTime()<Tree[index].getGenerateTime()){
                    swap(right,index);
                    shiftDown(right);
                }
            }
        }
        else{
            if(Tree[left].getGenerateTime()<Tree[right].getGenerateTime()){
                if(Tree[left].getGenerateTime()<Tree[index].getGenerateTime()){
                    swap(left,index);
                    shiftDown(left);
                }
            }
            else{
                return;
            }
        }
    }

    private void swap(int index_a, int index_b){
        TreeNode tmp;
        tmp = Tree[index_a];
        Tree[index_a] = Tree[index_b];
        Tree[index_b] = tmp;
        return;
    }

    public void setIsAllLoad(boolean flag){
        this.isAllLoad = flag;
        return;
    }

    public boolean getIsAllLoad(){
        return isAllLoad;
    }

    public int getSize(){
        return this.size;
    }
}
