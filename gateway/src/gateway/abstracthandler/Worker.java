package gateway.abstracthandler;


import cn.iie.gaia.util.StoppableLoopThread;

/**
 * Created with IntelliJ IDEA.
 * User: Rye
 * Date: 10/6/14
 * Time: 4:35 PM
 */
public abstract class Worker extends StoppableLoopThread {

    public abstract void work();

    @Override
    public void loopTask() {
        work();
    }
}
