package net.tamashi.fomekcore.api.guisystems;

import java.util.List;

/** State regression tests; no window, renderer, or Minecraft client is started. */
public class MenuState {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var dropdown=MenuControls.configure("choice","dropdown","B","",100,false,List.of("A","B","C"),2);
        check(MenuControls.text("choice").equals("B"),"default selection");
        dropdown.text="C";
        MenuControls.configure("choice","dropdown","A","",100,false,new String[]{"A","B","C"},2);
        check(MenuControls.text("choice").equals("C"),"selection persists across declarations");
        MenuControls.configure("choice","dropdown","A","",100,false,"D\nE",2);
        check(MenuControls.text("choice").equals("D"),"removed selection falls back to first option");
        var checkbox=MenuControls.configure("check","checkbox","","",100,true,List.of(),2);
        check(MenuControls.checked("check"),"checkbox default");checkbox.checked=false;
        MenuControls.configure("check","checkbox","","",100,true,List.of(),2);
        check(!MenuControls.checked("check"),"checkbox state persists");
        MenuStyle normal=MenuStyle.DEFAULT,hover=new MenuStyle(1,2,3,"","minecraft:default",9,"",true,"flat",true);
        MenuStyle held=new MenuStyle(2,3,4,"","minecraft:default",9,"",true,"flat",true),clicked=new MenuStyle(3,4,5,"","minecraft:default",9,"",true,"flat",true);
        StudioRuntime.beginFrame();StudioRuntime.push("button",normal,hover,held,clicked);String key=StudioRuntime.key();
        StudioRuntime.hit(key,0,0,100,30,"button");GuiState.updateMousePosition(5,5);
        check(StudioRuntime.style(key)==hover,"hover style");StudioRuntime.press(5,5);
        check(StudioRuntime.event("click")&&!StudioRuntime.event("click"),"click fires once");
        check(StudioRuntime.event("update")&&!StudioRuntime.event("update"),"update fires once per frame");
        StudioRuntime.checked(key);check(StudioRuntime.event("check")&&!StudioRuntime.event("check"),"check fires once");StudioRuntime.pop();
        StudioRuntime.beginFrame();StudioRuntime.push("button",normal,hover,held,clicked);StudioRuntime.hit(key,0,0,100,30,"button");
        check(StudioRuntime.style(key)==clicked,"one frame clicked style");check(StudioRuntime.event("update"),"next frame update");StudioRuntime.pop();
        StudioRuntime.beginFrame();StudioRuntime.push("button",normal,hover,held,clicked);StudioRuntime.hit(key,0,0,100,30,"button");
        check(StudioRuntime.style(key)==held,"held style after click frame");StudioRuntime.release();GuiState.updateMousePosition(200,200);
        check(StudioRuntime.style(key)==normal,"normal style after leaving");StudioRuntime.pop();
        MenuControls.clear();check(MenuControls.text("choice").isEmpty()&&!MenuControls.checked("check"),"close clears control state");
        System.out.println("PASS: defaults, selection persistence, lists, checkbox state, actions once per frame, and normal/hover/click/held styles");
    }
}
