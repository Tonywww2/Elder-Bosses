import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class VfxRenderCheck {
    private static final int SIZE = 192;

    public static void main(String[] arguments) throws Exception {
        boolean distortion = List.of(arguments).contains("--distortion");
        Path output = Path.of("build/ai-previews");
        Files.createDirectories(output);
        System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", output.resolve("lwjgl").toAbsolutePath().toString());
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW context unavailable");
        long window = 0;
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            window = GLFW.glfwCreateWindow(SIZE, SIZE, "Consort shader check", 0, 0);
            if (window == 0) throw new AssertionError("OpenGL 3.2 context unavailable");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            Path shaderRoot = Path.of("src/main/resources/assets/elder_bosses/shaders/core");
            String vertexSource = Files.readString(shaderRoot.resolve("consort_energy.vsh"));
            String fragmentSource = Files.readString(shaderRoot.resolve(distortion ? "consort_gravity.fsh" : "consort_energy.fsh"));
            int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            int vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            int vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[]{
                    -1,-1,0, 0,0, 1,0.7F,0.25F,1, 1,-1,0, 1,0, 1,0.7F,0.25F,1,
                    1,1,0, 1,1, 1,0.7F,0.25F,1, -1,1,0, 0,1, 1,0.7F,0.25F,1}, GL15.GL_STATIC_DRAW);
            attribute(program, "Position", 3, 0);
            attribute(program, "UV0", 2, 3 * Float.BYTES);
            attribute(program, "Color", 4, 5 * Float.BYTES);
            float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ModelViewMat"), false, identity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program,"ProjMat"), false, identity);
            GL20.glUniform4f(GL20.glGetUniformLocation(program,"ColorModulator"), 1,1,1,1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program,"InnerRatio"), 0);
            GL11.glViewport(0, 0, SIZE, SIZE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (distortion) {
                checkDistortion(program);
                GL15.glDeleteBuffers(vbo);
                GL30.glDeleteVertexArrays(vao);
                GL20.glDeleteProgram(program);
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(fragment);
                return;
            }
            List<Sample> results = new ArrayList<>();
            for (int mode : new int[]{2,4,10,11,12,13,14,15}) {
                byte[] first = null;
                for (float time : new float[]{1,2}) {
                    GL11.glClearColor(0,0,0,0);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GL20.glUniform1i(GL20.glGetUniformLocation(program,"EffectMode"), mode);
                    GL20.glUniform1f(GL20.glGetUniformLocation(program,"EffectTime"), time);
                    GL20.glUniform1f(GL20.glGetUniformLocation(program,"Progress"), time == 1 ? 0.2F : 0.65F);
                    GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
                    ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
                    try {
                        GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                        byte[] data = new byte[pixels.remaining()];
                        pixels.get(data);
                        int lit = 0, maximum = 0;
                        for (int index = 0; index < data.length; index += 4) {
                            int red = Byte.toUnsignedInt(data[index]);
                            maximum = Math.max(maximum, red);
                            if (red > 20) lit++;
                        }
                        if (mode == 14 ? lit != SIZE * SIZE : lit < 100 || lit >= SIZE * SIZE * 0.95 || maximum < 100) {
                            throw new AssertionError("Invisible or unmasked shader mode " + mode + ": " + lit + "/" + maximum);
                        }
                        if (first != null && Arrays.equals(first,data)) throw new AssertionError("Static shader mode " + mode);
                        if (mode == 2 || mode == 10) {
                            int previousAlpha = 256;
                            for (int horizontal = SIZE / 2; horizontal < SIZE - 2; horizontal += 8) {
                                int alpha = Byte.toUnsignedInt(data[(SIZE / 2 * SIZE + horizontal) * 4 + 3]);
                                if (alpha > previousAlpha + 1) throw new AssertionError("Light contains rings or repeated patterns instead of smooth falloff");
                                previousAlpha = alpha;
                            }
                            int coreAlpha = Byte.toUnsignedInt(data[(SIZE / 2 * SIZE + SIZE / 2) * 4 + 3]);
                            int glowAlpha = Byte.toUnsignedInt(data[(SIZE / 2 * SIZE + SIZE * 3 / 4) * 4 + 3]);
                            if (coreAlpha <= glowAlpha || glowAlpha == 0) throw new AssertionError("Missing dim outer light halo: mode" + mode + "/" + coreAlpha + "/" + glowAlpha);
                        }
                        first = data;
                        results.add(new Sample(mode,time,lit,maximum));
                    } finally {
                        MemoryUtil.memFree(pixels);
                    }
                }
            }
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL draw error");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fragmentSource.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var report = new Report("offscreen_shader_compile_and_pixel_checks_passed", GL11.glGetString(GL11.GL_RENDERER), hash, results,
                    "Actual project shaders; not in-world visual acceptance");
            Files.writeString(Path.of("models/promised_consort/vfx_render_validation.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            System.out.println("Layered effect shader passed: 8 modes, " + results.size() + " rendered frames; clean light falloff and halos checked; " + report.renderer());
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        } finally {
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void checkDistortion(int program) throws Exception {
        Path shaderRoot = Path.of("src/main/resources/assets/elder_bosses/shaders/core");
        var descriptor = com.google.gson.JsonParser.parseString(Files.readString(shaderRoot.resolve("consort_gravity.json"))).getAsJsonObject();
        for (var uniform : descriptor.getAsJsonArray("uniforms")) {
            var definition = uniform.getAsJsonObject();
            if (definition.getAsJsonArray("values").size() != definition.get("count").getAsInt()) {
                throw new AssertionError("Uniform size mismatch: " + definition.get("name"));
            }
            if (GL20.glGetUniformLocation(program, definition.get("name").getAsString()) < 0) {
                throw new AssertionError("Missing distortion uniform: " + definition.get("name"));
            }
        }
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        int sceneTexture = GL11.glGenTextures();
        int sceneFramebuffer = GL30.glGenFramebuffers();
        try {
            for (int row = 0; row < SIZE; row++) for (int column = 0; column < SIZE; column++) {
                pixels.put((byte) (((row / 8 + column / 8) % 2 == 0) ? 32 : 220));
                pixels.put((byte) (48 + column * 160 / SIZE));
                pixels.put((byte) (80 + row * 120 / SIZE));
                pixels.put((byte) 255);
            }
            pixels.flip();
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sceneTexture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new AssertionError("Scene framebuffer incomplete");
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDisable(GL11.GL_DITHER);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "SceneColor"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "ScreenSize"), SIZE, SIZE);
            byte[] baseline = new byte[pixels.remaining()];
            pixels.get(baseline);
            List<Integer> shiftedCounts = new ArrayList<>();
            byte[] previous = null;
            int animatedPixels = 0;
            for (int sample = 0; sample < 3; sample++) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
                GL30.glBlitFramebuffer(0, 0, SIZE, SIZE, 0, 0, SIZE, SIZE, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "Intensity"), sample == 0 ? 0 : 1);
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "EffectTime"), sample);
                GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
                pixels.clear();
                GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                byte[] current = new byte[pixels.remaining()];
                pixels.get(current);
                int shifted = 0;
                for (int row = 0; row < SIZE; row++) for (int column = 0; column < SIZE; column++) {
                    double radius = Math.hypot((column + 0.5) * 2 / SIZE - 1, (row + 0.5) * 2 / SIZE - 1);
                    int offset = (row * SIZE + column) * 4;
                    boolean changed = false;
                    boolean animated = false;
                    for (int channel = 0; channel < 3; channel++) {
                        int value = Byte.toUnsignedInt(current[offset + channel]);
                        int difference = Math.abs(value - Byte.toUnsignedInt(baseline[offset + channel]));
                        if ((sample == 0 || radius >= 1) && difference > 1) {
                            throw new AssertionError("Distortion changed an identity/outside pixel: " + sample + "/" + column + "/" + row);
                        }
                        changed |= difference > 2;
                        animated |= previous != null && Math.abs(value - Byte.toUnsignedInt(previous[offset + channel])) > 2;
                    }
                    if (radius > 0.42 && radius < 0.68) {
                        if (changed) shifted++;
                        if (sample == 2 && animated) animatedPixels++;
                    }
                }
                if (sample > 0 && shifted < 200) throw new AssertionError("Background was not refracted: " + shifted);
                shiftedCounts.add(shifted);
                previous = current;
            }
            if (animatedPixels < 100) throw new AssertionError("Distortion is static: " + animatedPixels);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL distortion error");
            var report = new com.google.gson.JsonObject();
            report.addProperty("status", "offscreen_scene_refraction_passed");
            report.addProperty("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.addProperty("fragment_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(shaderRoot.resolve("consort_gravity.fsh")))));
            report.add("shifted_annulus_pixels", new com.google.gson.Gson().toJsonTree(shiftedCounts));
            report.addProperty("animated_annulus_pixels", animatedPixels);
            report.addProperty("outside_pixels_unchanged", true);
            report.addProperty("zero_intensity_identity", true);
            report.addProperty("world_tested", false);
            Path reportFile = Path.of("models/promised_consort/vfx_render_validation.json");
            var document = Files.exists(reportFile) ? com.google.gson.JsonParser.parseString(Files.readString(reportFile)).getAsJsonObject() : new com.google.gson.JsonObject();
            document.add("gravity_distortion", report);
            Files.writeString(reportFile, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(document) + "\n");
            System.out.println("Gravity distortion passed: shifted annulus=" + shiftedCounts + ", animated=" + animatedPixels + "; zero/outside pixels unchanged; not in-world acceptance");
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(sceneFramebuffer);
            GL11.glDeleteTextures(sceneTexture);
            MemoryUtil.memFree(pixels);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static void attribute(int program, String name, int components, long offset) {
        int location = GL20.glGetAttribLocation(program, name);
        if (location < 0) throw new AssertionError("Missing shader attribute " + name);
        GL20.glEnableVertexAttribArray(location);
        GL20.glVertexAttribPointer(location, components, GL11.GL_FLOAT, false, 9 * Float.BYTES, offset);
    }

    private record Sample(int mode, float time, int litPixels, int peakRed) {}
    private record Report(String status, String renderer, String fragmentSha256, List<Sample> samples, String scope) {}
}