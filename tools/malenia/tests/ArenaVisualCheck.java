import com.google.gson.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;

/** Offline architectural review from the exported voxel composition and runtime textures/models.
 * Lighting is illustrative; this is not a screenshot of Minecraft's light engine. */
public final class ArenaVisualCheck {
    static final int W=1100,H=650;
    static Map<Long,String> cells=new HashMap<>();
    static Map<String,List<Float>> meshes=new LinkedHashMap<>();
    static Map<String,Integer> textures=new HashMap<>();
    static JsonArray[] flowerModels=new JsonArray[3];
    static int program,vao,vbo;
    static final int[][] DIR={{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    static final float[][][] FACES={{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},{{0,0,1},{0,0,0},{1,0,0},{1,0,1}},
        {{1,0,0},{0,0,0},{0,1,0},{1,1,0}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
        {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},{{1,0,1},{1,0,0},{1,1,0},{1,1,1}}};
    static long key(int x,int y,int z){return ((long)(x+128)<<20)|((long)(y+128)<<10)|(z+128);}
    static boolean solid(String s){return s!=null&&!s.contains("light[")&&!s.contains("white_petals")&&!s.contains("shallow_water")&&!s.contains("silt_slab");}
    public static void main(String[] args)throws Exception {
        Path output=Path.of("build/malenia-arena");
        System.setProperty("org.lwjgl.system.SharedLibraryExtractPath",output.resolve("lwjgl").toAbsolutePath().toString());
        var blocks=JsonParser.parseString(Files.readString(output.resolve("voxels.json"))).getAsJsonObject().getAsJsonArray("blocks");
        for(int i=0;i<3;i++)flowerModels[i]=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/models/block/haligtree_white_petals"+(i==0?"":"_"+(i+1))+".json"))).getAsJsonObject().getAsJsonArray("elements");
        for(var raw:blocks){var a=raw.getAsJsonArray();cells.put(key(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),a.get(3).getAsString());}
        require(GLFW.glfwInit(),"No GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(W,H,"Arena offline review",0,0);require(window!=0,"No OpenGL");
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
            program=program("#version 150\nin vec3 Position;in vec2 UV;in vec4 Color;uniform mat4 View;uniform mat4 Projection;out vec2 uv;out vec4 color;out float distance;void main(){vec4 p=View*vec4(Position,1);gl_Position=Projection*p;uv=UV;color=Color;distance=length(p.xyz);}",
                "#version 150\nin vec2 uv;in vec4 color;in float distance;uniform sampler2D Skin;uniform float Fog;out vec4 frag;void main(){vec4 t=texture(Skin,uv);if(t.a<.05)discard;vec3 c=t.rgb*color.rgb;float f=clamp((distance-24)/95,0,.42)*Fog;frag=vec4(mix(c,vec3(.07,.075,.07),f),t.a*color.a);}");
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);vbo=GL15.glGenBuffers();
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            var sheet=new BufferedImage(W*2,H*2,BufferedImage.TYPE_INT_RGB);
            var graphics=sheet.createGraphics();graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,22));
            float[][] cameras={{0,8,29,0,14,-28},{18,5,12,-7,15,-25},{0,10,-22,0,6,29},{59,58,75,0,9,0},{2.5f,2.0f,4.1f,2.5f,.35f,.5f}};
            String[] labels={"Entrance / rear root hollow","Water and white flower banks","From the root seat / stone vestibule","Cutaway / 52-block combat diameter"};
            for(int frame=0;frame<5;frame++){
                meshes.clear();boolean cut=frame==3;
                if(frame==4)for(int i=0;i<3;i++) {
                    flowers(i*2,0,0,i,i*90);
                    quad("haligtree_silt",new float[][]{{i*2,0,0},{i*2,0,1},{i*2+1,0,1},{i*2+1,0,0}},.8f,.8f,.8f,1);
                }
                else for(var raw:blocks){var a=raw.getAsJsonArray();int x=a.get(0).getAsInt(),y=a.get(1).getAsInt(),z=a.get(2).getAsInt();String s=a.get(3).getAsString();
                    if(hidden(x,y,z,cut)||s.contains("minecraft:light["))continue;
                    if(s.contains("white_petals")){flowers(x,y,z);continue;}
                    if(s.contains("shallow_water")){quad("water_still",new float[][]{{x,y+.025f,z},{x,y+.025f,z+1},{x+1,y+.025f,z+1},{x+1,y+.025f,z}},.48f,.60f,.61f,.78f);continue;}
                    boolean slab=s.contains("haligtree_silt_slab");
                    if(slab)quad("water_still",new float[][]{{x,y+.889f,z},{x,y+.889f,z+1},{x+1,y+.889f,z+1},{x+1,y+.889f,z}},.31f,.43f,.83f,.68f);
                    for(int f=0;f<6;f++){int nx=x+DIR[f][0],ny=y+DIR[f][1],nz=z+DIR[f][2];if(solid(cells.get(key(nx,ny,nz)))&&!hidden(nx,ny,nz,cut))continue;
                        float[][] q=new float[4][3];for(int i=0;i<4;i++){q[i][0]=x+FACES[f][i][0];q[i][1]=y+FACES[f][i][1]*(slab?.5f:1);q[i][2]=z+FACES[f][i][2];}
                        float light=new float[]{1,.46f,.77f,.68f,.62f,.86f}[f];if(!cut)light*=.74f+(float)Math.max(0,1-Math.hypot(x,z)/40)*.13f;
                        if(s.contains("froglight"))light=1.15f;
                        quad(textureName(s,f),q,light,light*.98f,light*.93f,1);
                    }
                }
                float[] c=cameras[frame];Matrix4f view=new Matrix4f().lookAt(c[0],c[1],c[2],c[3],c[4],c[5],0,1,0);
                Matrix4f projection=cut?new Matrix4f().ortho(-54,54,-54f*H/W,54f*H/W,.1f,250):frame==4?new Matrix4f().ortho(-3.2f,3.2f,-3.2f*H/W,3.2f*H/W,.1f,30):new Matrix4f().perspective((float)Math.toRadians(78),(float)W/H,.1f,180);
                GL11.glViewport(0,0,W,H);GL11.glClearColor(.055f,.061f,.057f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
                GL20.glUseProgram(program);GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"View"),false,view.get(new float[16]));
                GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"Projection"),false,projection.get(new float[16]));
                GL20.glUniform1f(GL20.glGetUniformLocation(program,"Fog"),cut?0:1);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Skin"),0);
                for(var mesh:meshes.entrySet())if(!mesh.getKey().equals("water_still"))draw(mesh.getKey(),mesh.getValue());
                GL11.glDepthMask(false);if(meshes.containsKey("water_still"))draw("water_still",meshes.get("water_still"));GL11.glDepthMask(true);
                BufferedImage image=pixels();javax.imageio.ImageIO.write(image,"png",output.resolve("view-"+frame+".png").toFile());
                if(frame==4){javax.imageio.ImageIO.write(image,"png",output.resolve("flower-model-review.png").toFile());continue;}
                int px=frame%2*W,py=frame/2*H;graphics.drawImage(image,px,py,null);graphics.setColor(new java.awt.Color(10,13,12,215));graphics.fillRect(px,py,W,44);
                graphics.setColor(java.awt.Color.WHITE);graphics.drawString(labels[frame],px+18,py+30);
            }
            graphics.dispose();javax.imageio.ImageIO.write(sheet,"png",output.resolve("arena-review.png").toFile());
            System.out.println("Four actual-voxel/runtime-texture arena views and flower model close-up rendered: "+output.resolve("arena-review.png"));
        }finally{GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    static boolean hidden(int x,int y,int z,boolean cut){return cut&&(y>21||(z>12&&y>5));}
    static String textureName(String state,int face){
        String name=state.replace("minecraft:","").replace("elder_bosses:","").split("\\[")[0];
        return switch(name){case "haligtree_silt_slab"->"haligtree_silt";case "haligtree_altar"->face==0?"haligtree_altar":face==1?"haligtree_silt":"haligtree_root";
            case "stripped_birch_wood"->"stripped_birch_log";case "ochre_froglight"->face<2?"ochre_froglight_top":"ochre_froglight_side";
            case "deepslate"->face<2?"deepslate_top":"deepslate";default->name;};
    }
    static void flowers(int x,int y,int z){flowers(x,y,z,Math.floorMod(x*31+z*17,3),Math.floorMod(x*13+z*7,4)*90);}
    static void flowers(int x,int y,int z,int variant,int turn){for(var raw:flowerModels[variant]){
        var element=raw.getAsJsonObject();var lo=element.getAsJsonArray("from");var hi=element.getAsJsonArray("to");
        float a=lo.get(0).getAsFloat()/16,b=lo.get(2).getAsFloat()/16,c=hi.get(0).getAsFloat()/16,d=hi.get(2).getAsFloat()/16,h=hi.get(1).getAsFloat()/16;
        float[][] points={{a,0,b},{c,0,d},{c,h,d},{a,h,b}};
        var rotation=element.getAsJsonObject("rotation");var origin=rotation.getAsJsonArray("origin");
        for(float[] p:points){rotate(p,origin.get(0).getAsFloat()/16,origin.get(2).getAsFloat()/16,rotation.get("angle").getAsFloat());rotate(p,.5f,.5f,turn);p[0]+=x;p[1]+=y;p[2]+=z;}
        quad("haligtree_white_petals",points,.96f,.94f,.87f,1);
    }}
    static void rotate(float[] p,float ox,float oz,float degrees){double angle=Math.toRadians(degrees);float dx=p[0]-ox,dz=p[2]-oz;p[0]=ox+(float)(dx*Math.cos(angle)-dz*Math.sin(angle));p[2]=oz+(float)(dx*Math.sin(angle)+dz*Math.cos(angle));}
    static void quad(String texture,float[][] p,float r,float g,float b,float alpha){var mesh=meshes.computeIfAbsent(texture,k->new ArrayList<>());float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int n:new int[]{0,1,2,0,2,3})for(float f:new float[]{p[n][0],p[n][1],p[n][2],uv[n][0],uv[n][1],r,g,b,alpha})mesh.add(f);
    }
    static void draw(String name,List<Float> values)throws Exception{int id=textures.containsKey(name)?textures.get(name):texture(name);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        float[] data=new float[values.size()];for(int i=0;i<data.length;i++)data[i]=values.get(i);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        attribute("Position",3,0);attribute("UV",2,3);attribute("Color",4,5);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,data.length/9);
        require(GL11.glGetError()==0,"OpenGL error");
    }
    static int texture(String name)throws Exception{var stream=ArenaVisualCheck.class.getResourceAsStream("/assets/"+(name.startsWith("haligtree_")?"elder_bosses":"minecraft")+"/textures/block/"+name+".png");require(stream!=null,"Missing runtime texture "+name);
        BufferedImage image;try(stream){image=javax.imageio.ImageIO.read(stream);}int size=image.getWidth();var buffer=MemoryUtil.memAlloc(size*size*4);
        try{for(int y=0;y<size;y++)for(int x=0;x<size;x++){int color=image.getRGB(x,y);buffer.put((byte)(color>>16)).put((byte)(color>>8)).put((byte)color).put((byte)(color>>24));}
            buffer.flip();int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);textures.put(name,id);return id;
        }finally{MemoryUtil.memFree(buffer);}
    }
    static void attribute(String n,int size,int offset){int a=GL20.glGetAttribLocation(program,n);GL20.glEnableVertexAttribArray(a);GL20.glVertexAttribPointer(a,size,GL11.GL_FLOAT,false,36,(long)offset*4);}
    static int program(String v,String f){int p=GL20.glCreateProgram();for(var e:Map.of(GL20.GL_VERTEX_SHADER,v,GL20.GL_FRAGMENT_SHADER,f).entrySet()){int s=GL20.glCreateShader(e.getKey());GL20.glShaderSource(s,e.getValue());GL20.glCompileShader(s);require(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(s));GL20.glAttachShader(p,s);}GL20.glLinkProgram(p);require(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)!=0,GL20.glGetProgramInfoLog(p));return p;}
    static BufferedImage pixels(){var b=MemoryUtil.memAlloc(W*H*4);try{GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int at=(y*W+x)*4;image.setRGB(x,H-1-y,((b.get(at)&255)<<16)|((b.get(at+1)&255)<<8)|(b.get(at+2)&255));}return image;}finally{MemoryUtil.memFree(b);}}
    static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
}
