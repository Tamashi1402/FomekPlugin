import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import java.nio.file.*;
import java.util.concurrent.*;

public class WebViewStudioTest {
    public static void main(String[] args)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={null};
        Platform.startup(()->{
            WebView web=new WebView();web.setPrefSize(1500,1000);web.resize(1500,1000);
            StackPane root=new StackPane(web);new Scene(root,1500,1000);root.resize(1500,1000);root.applyCss();root.layout();
            web.getEngine().getLoadWorker().stateProperty().addListener((o,b,state)->{
                if(state==Worker.State.SUCCEEDED){try{result[0]=(String)web.getEngine().executeScript("runWebViewTest()");}catch(Throwable e){result[0]=e.toString();}done.countDown();}
                if(state==Worker.State.FAILED){result[0]=String.valueOf(web.getEngine().getLoadWorker().getException());done.countDown();}
            });
            web.getEngine().load(Path.of(args[0]).toUri().toString());
        });
        if(!done.await(45,TimeUnit.SECONDS))result[0]="TIMEOUT";
        Platform.exit();System.out.println(result[0]);if(result[0]==null||!result[0].startsWith("PASS"))System.exit(1);
    }
}
