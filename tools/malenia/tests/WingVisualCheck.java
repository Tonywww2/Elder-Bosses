import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.client.vfx.ClientMaleniaAeonia;
import com.tonywww.elder_bosses.client.vfx.MaleniaEffectState;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.client.PlatformMaleniaPhantomRenderer;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.util.JsonUtil;
import software.bernie.geckolib.cache.object.GeoBone;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Compile the actual rot-wing shaders and render the actual body plus GPU wings. No client/world substitute. */
public final class WingVisualCheck {
    private static final int W=720,H=640;
    private static int vao,vbo;
    public static void main(String[] args) throws Exception {
        Path output=Path.of("build/malenia-v16/previews");Files.createDirectories(output);
        if(!GLFW.glfwInit())throw new AssertionError("No GLFW context");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"Rot wing shader check",0,0);
        require(window!=0,"No GL 3.2 context");
        try {
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            Path shaders=Path.of("src/main/resources/assets/elder_bosses/shaders/core");
            int wings=program(Files.readString(shaders.resolve("malenia_wings.vsh")),Files.readString(shaders.resolve("malenia_wings.fsh")));
            int body=program(Files.readString(shaders.resolve("malenia_effect.vsh")),"#version 150\nin vec4 vertexColor; in vec2 effectUv; uniform sampler2D Skin; out vec4 fragColor;\nvoid main(){fragColor=texture(Skin,effectUv)*vertexColor;if(fragColor.a<0.01)discard;}");
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);vbo=GL15.glGenBuffers();
            GL11.glViewport(0,0,W,H);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);
            Model raw=JsonUtil.GEO_GSON.fromJson(Files.readString(Path.of("tools/malenia/geo/malenia_phase_two.geo.json")),Model.class);
            var baked=BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
            var library=com.google.gson.JsonParser.parseString(Files.readString(Path.of("tools/malenia/animations/malenia.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
            var animations=JsonUtil.GEO_GSON.fromJson(library,software.bernie.geckolib.loading.object.BakedAnimations.class);
            var init=software.bernie.geckolib.GeckoLib.class.getDeclaredField("hasInitialized");init.setAccessible(true);init.setBoolean(null,true);
            var cache=software.bernie.geckolib.cache.GeckoLibCache.class.getDeclaredField("ANIMATIONS");cache.setAccessible(true);
            cache.set(null,Map.of(com.tonywww.elder_bosses.platforms.PlatformResourceLocation.id("animations/malenia/malenia.animation.json"),animations));
            var renderer=new PlatformMaleniaPhantomRenderer();renderer.preparePose(baked,0,0,true);
            var field=renderer.getClass().getDeclaredField("roots");field.setAccessible(true);
            @SuppressWarnings("unchecked") List<GeoBone> roots=(List<GeoBone>)field.get(renderer);
            Map<String,GeoBone> bones=new HashMap<>();for(var root:roots)collect(root,bones);
            int skin=texture(Path.of("tools/malenia/textures/malenia_phase_two.png"));
            BufferedImage sheet=new BufferedImage(W*3,H*2,BufferedImage.TYPE_INT_RGB);
            var labels=sheet.createGraphics();labels.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,20));
            String[] names={"Front / t=0","Front / t=1.6","Side / layered depth","Back / broken silhouette","Attack / torso follows","Aeonia / folded"};
            BufferedImage first=null;
            for(int frame=0;frame<6;frame++) {
                renderer.preparePose(baked,0,0,true);
                String clip=frame==4?"winged_sweep":frame==5?"scarlet_aeonia":"idle_phase_two";
                apply(renderer.getGeoModel().getAnimation(null,"animation.malenia."+clip),frame==4?18:frame==5?48:0,bones);
                bones.get("aeonia_core").setHidden(true);bones.get("aeonia_core").setChildrenHidden(true);
                Capture capture=new Capture();PoseStack poses=new PoseStack();
                for(var root:roots)renderer.renderRecursively(poses,null,root,null,null,capture,true,0,0xF000F0,0,1,1,1,1);
                float angle=frame==2?82:frame==3?180:frame==4?32:0;
                float radians=(float)Math.toRadians(angle);
                Matrix4f view=new Matrix4f().lookAt((float)Math.sin(radians)*14,5,-(float)Math.cos(radians)*14,0,2.8F,0,0,1,0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,skin);GL11.glDepthMask(true);
                draw(body,capture,view,5.3F,0,true);
                Matrix4f anchor=com.tonywww.elder_bosses.client.vfx.MaleniaWingGeometry.anchor(bones.get("chest"));
                GL20.glUseProgram(wings);
                GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(wings,"WingMatrix"),false,anchor.get(new float[16]));
                GL20.glUniform1f(GL20.glGetUniformLocation(wings,"EffectTime"),frame==0?0:1.6F);
                GL20.glUniform1f(GL20.glGetUniformLocation(wings,"Spread"),frame==5?.22F:1);
                GL20.glUniform1f(GL20.glGetUniformLocation(wings,"Activity"),frame>=4?1:.25F);
                GL20.glUniform1f(GL20.glGetUniformLocation(wings,"FogStart"),128);
                GL20.glUniform1f(GL20.glGetUniformLocation(wings,"FogEnd"),256);
                GL20.glUniform4f(GL20.glGetUniformLocation(wings,"FogColor"),0,0,0,0);
                capture=new Capture();com.tonywww.elder_bosses.client.vfx.MaleniaWingGeometry.emit(capture,frame==5?.75F:1,.37F,frame==3);
                require(capture.vertices.size()==7680,"Unexpected support-grid budget");
                GL11.glDepthMask(false);draw(wings,capture,view,5.3F,0,false);GL11.glDepthMask(true);
                BufferedImage image=readPixels();
                javax.imageio.ImageIO.write(image,"png",output.resolve("wing-"+frame+".png").toFile());
                if(frame==0)first=image;
                if(frame==1) { int changed=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(first.getRGB(x,y)!=image.getRGB(x,y))changed++;
                    require(changed>2000,"Wing shader did not animate: "+changed);System.out.println("Animated pixels: "+changed); }
                labels.drawImage(image,frame%3*W,frame/3*H,null);label(labels,names[frame],frame%3*W,frame/3*H);
            }
            labels.dispose();javax.imageio.ImageIO.write(sheet,"png",output.resolve("shader-wings.png").toFile());
            System.out.println("Actual wing GLSL compilation, 6 views and animated-pixel check passed: "+output);
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void collect(GeoBone bone,Map<String,GeoBone> bones){bones.put(bone.getName(),bone);for(var child:bone.getChildBones())collect(child,bones);}
    private static void apply(software.bernie.geckolib.core.animation.Animation animation,double tick,Map<String,GeoBone> bones) {
        for(var track:animation.boneAnimations()) {
            var bone=bones.get(track.boneName()); if(bone==null)continue;
            for(int channel=0;channel<3;channel++) {
                var stack=channel==0?track.rotationKeyFrames():channel==1?track.positionKeyFrames():track.scaleKeyFrames();
                var axes=List.of(stack.xKeyframes(),stack.yKeyframes(),stack.zKeyframes());float[] value=new float[3];boolean present=true;
                for(int axis=0;axis<3;axis++) {
                    var keys=axes.get(axis);if(keys.isEmpty()){present=false;break;}
                    value[axis]=(float)keys.get(keys.size()-1).endValue().get();double cursor=0;
                    for(var key:keys){if(tick<=cursor+key.length()){double t=key.length()==0?1:Math.max(0,Math.min(1,(tick-cursor)/key.length()));
                        value[axis]=(float)(key.startValue().get()+t*(key.endValue().get()-key.startValue().get()));break;}cursor+=key.length();}
                }
                if(!present)continue;
                if(channel==0){var rest=bone.getInitialSnapshot();bone.setRotX(rest.getRotX()+value[0]);bone.setRotY(rest.getRotY()+value[1]);bone.setRotZ(rest.getRotZ()+value[2]);}
                else if(channel==1){bone.setPosX(value[0]);bone.setPosY(value[1]);bone.setPosZ(value[2]);}
                else{bone.setScaleX(value[0]);bone.setScaleY(value[1]);bone.setScaleZ(value[2]);}
            }
        }
    }
    private static void draw(int program,Capture capture,Matrix4f view,float size,int mode) {
        draw(program,capture,view,size,mode,true);
    }
    private static void draw(int program,Capture capture,Matrix4f view,float size,int mode,boolean clear) {
        if (clear) { GL11.glClearColor(0.035F,0.045F,0.065F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT); }
        GL20.glUseProgram(program);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewMat"),false,view.get(new float[16]));
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjMat"),false,new Matrix4f().ortho(-size,size,-size*H/W,size*H/W,0.1F,100).get(new float[16]));
        GL20.glUniform4f(GL20.glGetUniformLocation(program,"ColorModulator"),1,1,1,1);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"EffectMode"),mode);
        GL20.glUniform1i(GL20.glGetUniformLocation(program,"Skin"),0);
        List<float[]> triangles = new ArrayList<>();
        for(int i=0;i<capture.vertices.size();i+=4) for(int n:new int[]{0,1,2,0,2,3}) triangles.add(capture.vertices.get(i+n));
        float[] data=new float[triangles.size()*9]; int cursor=0;
        for(var v:triangles)for(float f:v){require(Float.isFinite(f),"Nonfinite render vertex");data[cursor++]=f;}
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        attribute(program,"Position",3,0);attribute(program,"UV0",2,3);attribute(program,"Color",4,5);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,triangles.size());
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL draw error");
    }
    private static void attribute(int p,String n,int count,int offset){int at=GL20.glGetAttribLocation(p,n);GL20.glEnableVertexAttribArray(at);GL20.glVertexAttribPointer(at,count,GL11.GL_FLOAT,false,9*4,(long)offset*4);}
    private static int program(String vertex,String fragment){int p=GL20.glCreateProgram();GL20.glAttachShader(p,compile(GL20.GL_VERTEX_SHADER,vertex));GL20.glAttachShader(p,compile(GL20.GL_FRAGMENT_SHADER,fragment));GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
    private static int compile(int kind,String source){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,source);GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));return s;}
    private static BufferedImage readPixels(){var b=MemoryUtil.memAlloc(W*H*4);try{GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);int visible=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++){int at=(y*W+x)*4,r=b.get(at)&255,g=b.get(at+1)&255,blue=b.get(at+2)&255;if(r>25||g>25||blue>25)visible++;image.setRGB(x,H-1-y,(r<<16)|(g<<8)|blue);}require(visible>100,"Invisible rendered effect");return image;}finally{MemoryUtil.memFree(b);}}
    private static int texture(Path path)throws Exception{var image=javax.imageio.ImageIO.read(path.toFile());var b=MemoryUtil.memAlloc(image.getWidth()*image.getHeight()*4);try{for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}b.flip();int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return id;}finally{MemoryUtil.memFree(b);}}
    private static void label(java.awt.Graphics2D g,String text,int x,int y){g.setColor(java.awt.Color.WHITE);g.drawString(text,x+14,y+26);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class Capture implements VertexConsumer {
        private final List<float[]> vertices=new ArrayList<>(); private final float[] current=new float[9];
        public VertexConsumer vertex(double x,double y,double z){current[0]=(float)x;current[1]=(float)y;current[2]=(float)z;return this;}
        public VertexConsumer uv(float u,float v){current[3]=u;current[4]=v;return this;}
        public VertexConsumer color(int r,int g,int b,int a){current[5]=r/255F;current[6]=g/255F;current[7]=b/255F;current[8]=a/255F;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}public VertexConsumer uv2(int u,int v){return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}public void endVertex(){vertices.add(current.clone());}
        public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}public void unsetDefaultColor(){}
    }
}
