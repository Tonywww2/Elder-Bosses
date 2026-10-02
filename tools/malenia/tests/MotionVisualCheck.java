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

/** Renders the actual projection bones and flower geometry in a hidden OpenGL context. Not an in-world test. */
public final class MotionVisualCheck {
    private static final int W = 512, H = 384;
    private static int vao, vbo;
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/malenia-v15/previews"); Files.createDirectories(output);
        System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", output.resolve("lwjgl").toAbsolutePath().toString());
        if (!GLFW.glfwInit()) throw new AssertionError("No GLFW context");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(W, H, "Malenia offscreen check", 0, 0);
        if (window == 0) throw new AssertionError("No OpenGL 3.2 context");
        try {
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            Path shaders = Path.of("src/main/resources/assets/elder_bosses/shaders/core");
            String vertex = Files.readString(shaders.resolve("malenia_effect.vsh"));
            int effect = program(vertex, Files.readString(shaders.resolve("malenia_effect.fsh")));
            int textured = program(vertex, "#version 150\nin vec4 vertexColor; in vec2 effectUv; uniform sampler2D Skin; out vec4 fragColor;\nvoid main(){fragColor=texture(Skin,effectUv)*vertexColor;if(fragColor.a<0.01)discard;}");
            vao = GL30.glGenVertexArrays(); GL30.glBindVertexArray(vao); vbo = GL15.glGenBuffers();
            GL11.glViewport(0,0,W,H); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL);
            Model raw = JsonUtil.GEO_GSON.fromJson(Files.readString(Path.of("src/main/resources/assets/elder_bosses/geo/malenia/malenia.geo.json")),Model.class);
            var baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
            var library = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/animations/malenia/malenia.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
            var animations = JsonUtil.GEO_GSON.fromJson(library,software.bernie.geckolib.loading.object.BakedAnimations.class);
            var initialized = software.bernie.geckolib.GeckoLib.class.getDeclaredField("hasInitialized"); initialized.setAccessible(true); initialized.setBoolean(null,true);
            var animationCache = software.bernie.geckolib.cache.GeckoLibCache.class.getDeclaredField("ANIMATIONS"); animationCache.setAccessible(true);
            animationCache.set(null,Map.of(com.tonywww.elder_bosses.platforms.PlatformResourceLocation.id("animations/malenia/malenia.animation.json"),animations));
            var renderer = new PlatformMaleniaPhantomRenderer();
            int texture = texture(Path.of("src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia.png"));
            renderer.preparePose(baked,0,0,true);
            var field=renderer.getClass().getDeclaredField("roots"); field.setAccessible(true);
            @SuppressWarnings("unchecked") List<GeoBone> roots=(List<GeoBone>)field.get(renderer);
            Map<String,GeoBone> bones=new HashMap<>(); for(var root:roots) collect(root,bones);
            String[] clips={"single_slash","double_slash","thrust","upward_combo","grab_impale","waterfowl_dance","scarlet_aeonia","idle_phase_one","run"};
            double[][] times={{7,9,10,12,17,27},{8,11,13,25,29,31},{17,20,22,25,36,50},{15,18,23,45,48,54},
                {18,24,28,40,44,54},{22,32,39,53,62,70,77,82,90,105,110,121},{26,43,51,61,70,86},{0,0,0,0,0,0},{0,2,4,6,8,10}};
            Set<String> armor=Set.of("helm","armor_torso","armor_shoulder_l","armor_shoulder_r","armor_waist","cape_01","skirt_front","skirt_back","skirt_l","skirt_r");
            for(int c=0;c<clips.length;c++) {
                BufferedImage sheet=new BufferedImage(W*3,H*((times[c].length+2)/3),BufferedImage.TYPE_INT_RGB);
                var labels=sheet.createGraphics(); labels.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,18));
                for(int f=0;f<times[c].length;f++) {
                    boolean phaseTwo=clips[c].equals("scarlet_aeonia");
                    for(var bone:bones.values()) {
                        var rest=bone.getInitialSnapshot();
                        bone.setRotX(rest.getRotX());bone.setRotY(rest.getRotY());bone.setRotZ(rest.getRotZ());
                        bone.setPosX(0);bone.setPosY(0);bone.setPosZ(0);bone.setScaleX(1);bone.setScaleY(1);bone.setScaleZ(1);
                        String n=bone.getName(); boolean hide=phaseTwo?armor.contains(n):n.startsWith("wing_root")||n.startsWith("phase_two");
                        if(n.equals("aeonia_core"))hide=!phaseTwo;
                        bone.setHidden(hide);bone.setChildrenHidden(hide);
                    }
                    apply(renderer.getGeoModel().getAnimation(null,"animation.malenia."+clips[c]),times[c][f],bones);
                    if(clips[c].equals("idle_phase_one")&&f==5) apply(renderer.getGeoModel().getAnimation(null,"animation.malenia.grab_impale"),36,bones);
                    Capture capture=new Capture();PoseStack poses=new PoseStack();
                    for(var root:roots)renderer.renderRecursively(poses,null,root,null,null,capture,true,0,0xF000F0,0,1,1,1,1);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                    Matrix4f view=new Matrix4f().lookAt(6,4,-8,0,2.5F,0,0,1,0);float size=phaseTwo?7.2F:4.8F;
                    if(clips[c].equals("idle_phase_one")) {
                        if(f<3||f==5) {
                            var center=com.tonywww.elder_bosses.client.vfx.MaleniaWingGeometry.anchor(bones.get(f==0||f==5?"hand_l":"blade_mount"))
                                    .getTranslation(new org.joml.Vector3f()).add(0,-.1F,0);
                            view=new Matrix4f().lookAt(center.x+(f==2?-2:1),center.y+.35F,center.z+(f==2?3:-3),center.x,center.y,center.z,0,1,0);size=.7F;
                        }else{view=new Matrix4f().lookAt(f==4?8:0,3.5F,10,0,2.4F,0,0,1,0);size=3.6F;}
                    }
                    if(clips[c].equals("run")){view=new Matrix4f().lookAt(7,4,8,0,2.4F,0,0,1,0);size=3.6F;}
                    draw(textured,capture,view,size,0);
                    labels.drawImage(readPixels(),f%3*W,f/3*H,null);label(labels,clips[c]+" / tick "+times[c][f],f%3*W,f/3*H);
                }
                labels.dispose();javax.imageio.ImageIO.write(sheet,"png",output.resolve(clips[c]+".png").toFile());
            }
            System.out.println("GeckoLib animation/model contact sheets rendered: "+output);
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
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
