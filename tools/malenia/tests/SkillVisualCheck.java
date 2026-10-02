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
public final class SkillVisualCheck {
    private static final int W = 512, H = 384;
    private static int vao, vbo;
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/malenia-v16/previews"); Files.createDirectories(output);
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
            BufferedImage sheet = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_RGB);
            var labels = sheet.createGraphics(); labels.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 18));
            var packet = new IndicatorSnapshotPacket(7,"9:scarlet_aeonia:zone",IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                    IndicatorSnapshotPacket.StyleRole.SCARLET_ROT_DARK_RED,IndicatorSnapshotPacket.Semantic.SCARLET_ROT,
                    IndicatorSnapshotPacket.IndicatorState.PERSISTENT,IndicatorSnapshotPacket.ShapeType.ZONE,
                    new IndicatorSnapshotPacket.Point(0,0,0),0,List.of(8.25F),List.of(),100,100,100,184,false);
            var segment = new MaleniaEffectState.Segment(packet,MaleniaEffectState.identity(packet.indicatorId()),184);
            for (int i = 0; i < 3; i++) {
                Capture capture = new Capture();
                double age = new double[]{0.5,7,18}[i];
                ClientMaleniaAeonia.render(capture,new PoseStack().last(),segment,100+age);
                require(capture.vertices.size() == 42 * 4 * 4 * 4,"Flower lost petal strips");
                draw(effect,capture,new Matrix4f().lookAt(12,13,17,0,0.5F,0,0,1,0),14,1);
                Capture ground = new Capture();
                ClientMaleniaAeonia.ground(ground,new PoseStack().last(),segment,100+age);
                draw(effect,ground,new Matrix4f().lookAt(12,13,17,0,0.5F,0,0,1,0),14,0,false);
                labels.drawImage(readPixels(),i*W,H,null); label(labels,"Aeonia +"+age+" ticks",i*W,H);
            }
            Model raw = JsonUtil.GEO_GSON.fromJson(Files.readString(Path.of("src/main/resources/assets/elder_bosses/geo/malenia/malenia_phase_two.geo.json")),Model.class);
            var baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
            var library = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/animations/malenia/malenia.animation.json"))).getAsJsonObject().getAsJsonObject("animations");
            var animations = JsonUtil.GEO_GSON.fromJson(library,software.bernie.geckolib.loading.object.BakedAnimations.class);
            var initialized = software.bernie.geckolib.GeckoLib.class.getDeclaredField("hasInitialized"); initialized.setAccessible(true); initialized.setBoolean(null,true);
            var animationCache = software.bernie.geckolib.cache.GeckoLibCache.class.getDeclaredField("ANIMATIONS"); animationCache.setAccessible(true);
            animationCache.set(null,Map.of(com.tonywww.elder_bosses.platforms.PlatformResourceLocation.id("animations/malenia/malenia.animation.json"),animations));
            var renderer = new PlatformMaleniaPhantomRenderer();
            int texture = texture(Path.of("src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia_phase_two.png"));
            for (int i = 0; i < 3; i++) {
                renderer.preparePose(baked,i == 0 ? 0.25 : 1,i == 2 ? 4 : 0,true);
                var field = renderer.getClass().getDeclaredField("roots"); field.setAccessible(true);
                @SuppressWarnings("unchecked") List<GeoBone> roots = (List<GeoBone>)field.get(renderer);
                Capture capture = new Capture(); PoseStack poses = new PoseStack();
                for (var root : roots) renderer.renderRecursively(poses,null,root,null,null,capture,true,0,0xF000F0,0,1,0.70F,0.58F,0.65F);
                require(capture.vertices.size()>600,"Projection lost body geometry");
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                draw(textured,capture,new Matrix4f().lookAt(6,4,-8,0,2.6F,0,0,1,0),4.8F,0);
                labels.drawImage(readPixels(),i*W,0,null); label(labels,new String[]{"Phantom / windup","Phantom / sweep","Phantom / thrust"}[i],i*W,0);
            }
            labels.dispose(); javax.imageio.ImageIO.write(sheet,"png",output.resolve("skill-effects.png").toFile());
            int cue = program(vertex, Files.readString(shaders.resolve("malenia_parry_cue.fsh")));
            BufferedImage halos = new BufferedImage(W*3,H*2,BufferedImage.TYPE_INT_RGB);
            var haloLabels=halos.createGraphics();haloLabels.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,18));
            for(int i=0;i<6;i++) {
                GL20.glUseProgram(cue);
                GL20.glUniform1f(GL20.glGetUniformLocation(cue,"Success"),i<3?0:1);
                GL20.glUniform1f(GL20.glGetUniformLocation(cue,"EffectTime"),i*.15F);
                GL20.glUniform1f(GL20.glGetUniformLocation(cue,"Progress"),(i%3)*.4F);
                GL20.glUniform1f(GL20.glGetUniformLocation(cue,"PulseCount"),3);
                GL20.glUniform3f(GL20.glGetUniformLocation(cue,"CueColor"),1,.125F,.125F);
                Capture halo=new Capture();
                com.tonywww.elder_bosses.client.vfx.MaleniaEffectGeometry.quad(halo,new PoseStack().last(),
                        new net.minecraft.world.phys.Vec3(-1,-1,0),new net.minecraft.world.phys.Vec3(1,-1,0),
                        new net.minecraft.world.phys.Vec3(1,1,0),new net.minecraft.world.phys.Vec3(-1,1,0),0xFFFFFF,1);
                draw(cue,halo,new Matrix4f().lookAt(0,0,4,0,0,0,0,1,0),1.5F,0);
                haloLabels.drawImage(readPixels(),i%3*W,i/3*H,null);label(haloLabels,(i<3?"Parry window / ":"Parry success / ")+(i%3*40)+"%",i%3*W,i/3*H);
            }
            haloLabels.dispose();javax.imageio.ImageIO.write(halos,"png",output.resolve("parry-halo.png").toFile());
            System.out.println("Actual shader compilation and six offscreen model/flower frames passed: "+output.resolve("skill-effects.png"));
        } finally { GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
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
