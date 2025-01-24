package com.gaia3d.renderer.engine;

import com.gaia3d.basic.exchangable.GaiaBufferDataSet;
import com.gaia3d.basic.exchangable.GaiaSet;
import com.gaia3d.basic.geometry.GaiaBoundingBox;
import com.gaia3d.basic.geometry.GaiaRectangle;
import com.gaia3d.basic.geometry.octree.HalfEdgeOctree;
import com.gaia3d.basic.halfedge.*;
import com.gaia3d.basic.model.*;
import com.gaia3d.basic.types.TextureType;
import com.gaia3d.renderer.engine.dataStructure.GaiaScenesContainer;
import com.gaia3d.renderer.engine.fbo.Fbo;
import com.gaia3d.renderer.engine.fbo.FboManager;
import com.gaia3d.renderer.engine.graph.*;
import com.gaia3d.renderer.engine.scene.Camera;
import com.gaia3d.renderer.engine.scene.Projection;
import com.gaia3d.renderer.engine.screen.ScreenQuad;
import com.gaia3d.renderer.renderable.RenderableGaiaScene;
import com.gaia3d.renderer.renderable.RenderablePrimitive;
import com.gaia3d.renderer.renderable.SelectionColorManager;
import com.gaia3d.util.GaiaSceneUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.joml.*;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Math;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

@Getter
@Setter
@Slf4j
public class Engine {
    GaiaScenesContainer gaiaScenesContainer;
    SelectionColorManager selectionColorManager;
    List<HalfEdgeScene> halfEdgeScenes = new ArrayList<>();
    List<GaiaScene> gaiaScenes = new ArrayList<>();
    private Window window;
    private ShaderManager shaderManager;
    private RenderEngine renderer;
    private HalfEdgeRenderer halfEdgeRenderer;
    private FboManager fboManager;
    private ScreenQuad screenQuad;
    private Camera camera;
    private double midButtonXpos = 0;
    private double midButtonYpos = 0;
    private double leftButtonXpos = 0;
    private double leftButtonYpos = 0;
    private boolean leftButtonClicked = false;
    private boolean midButtonClicked = false;
    private boolean renderAxis = false;

    private int boxRenderingMaxSize = 1024;

    private int testsCount = 0;
    private String tempFolderPath = "D:\\Result_mago3dTiler\\temp";

    public Engine(String windowTitle, Window.WindowOptions opts, IAppLogic appLogic) {
        window = new Window(windowTitle, opts, () -> {
            resize();
            return null;
        });

    }

    private boolean checkGlError() {
        int glError = GL20.glGetError();
        if (glError != GL20.GL_NO_ERROR) {
            log.error("glError: {}", glError);
            return true;
        }
        return false;
    }

    public void deleteObjects() {
        fboManager.deleteAllFbos();
        shaderManager.deleteAllShaderPrograms();
        screenQuad.cleanup();
        window.cleanup();
    }


    private void resize() {
        //scene.resize(window.getWidth(), window.getHeight());
    }

    public void run() throws IOException {
        log.info("Hello LWJGL " + Version.getVersion() + "!");

        init();
        loop();

        long windowHandle = window.getWindowHandle();
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public GaiaScenesContainer getGaiaScenesContainer() {
        if (gaiaScenesContainer == null) {
            int windowWidth = window.getWidth();
            int windowHeight = window.getHeight();
            gaiaScenesContainer = new GaiaScenesContainer(windowWidth, windowHeight);
        }
        return gaiaScenesContainer;
    }

    public FboManager getFboManager() {
        if (fboManager == null) {
            fboManager = new FboManager();
        }
        return fboManager;
    }

    public void getRenderSceneImage(ShaderProgram sceneShaderProgram) {
        //***********************************************************
        // Note : before to call this function, must bind the fbo.***
        //***********************************************************
        sceneShaderProgram.bind();

        Camera camera = gaiaScenesContainer.getCamera();
        Matrix4d modelViewMatrix = camera.getModelViewMatrix();
        UniformsMap uniformsMap = sceneShaderProgram.getUniformsMap();
        uniformsMap.setUniformMatrix4fv("uModelViewMatrix", new Matrix4f(modelViewMatrix));

        renderer.render(gaiaScenesContainer, sceneShaderProgram);

        sceneShaderProgram.unbind();
    }

    public void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (window == null) {
            window = new Window("Mago3D", new Window.WindowOptions(), () -> {
                resize();
                return null;
            });

            GL.createCapabilities();
        }

        long windowHandle = window.getWindowHandle();

        // 마우스 위치 콜백
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            if (this.midButtonClicked) {
                Vector3d pivot = new Vector3d(0.0d, 0.0d, -1.0d);
                float xoffset = (float) (this.midButtonXpos - xpos) * 0.01f;
                float yoffset = (float) (this.midButtonYpos - ypos) * 0.01f;
                camera.rotationOrbit(xoffset, yoffset, pivot);
            }
            this.midButtonXpos = xpos;
            this.midButtonYpos = ypos;

            if (this.leftButtonClicked) {
                // translate camera
                Vector3d translation = new Vector3d((xpos - this.leftButtonXpos) * 0.01f, (ypos - this.leftButtonYpos) * 0.01f, 0);
                //translation.y *= -1;
                camera.translate(translation);
            }

            this.leftButtonXpos = xpos;
            this.leftButtonYpos = ypos;
        });


        // 마우스 버튼 이벤트
        glfwSetMouseButtonCallback(windowHandle, (window, key, action, mode) -> {
            if (key == GLFW_MOUSE_BUTTON_3 && action == GLFW_PRESS) {
                this.midButtonClicked = true;
            } else if (key == GLFW_MOUSE_BUTTON_3 && action == GLFW_RELEASE) {
                this.midButtonClicked = false;
            }

            // check left button
            if (key == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                this.leftButtonClicked = true;
            } else if (key == GLFW_MOUSE_BUTTON_1 && action == GLFW_RELEASE) {
                this.leftButtonClicked = false;
            }

        });

        glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
            camera.moveFront((float) yoffset * 10.0f);
        });

        // 키보드 콜백 이벤트를 설정합니다. 키를 눌렀을 때, 누르고 있을 때, 떼었을 때에 따라 바꿔줍니다.
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
            }

            if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                // keep the camera position and target.***
                Vector3d keepCameraPosition = new Vector3d(camera.getPosition());
                Vector3d keepCameraDirection = new Vector3d(camera.getDirection());
                Vector3d keepCameraUp = new Vector3d(camera.getUp());

                // do an iteration of decimation.***
                List<HalfEdgeScene> decimatedScenes = new ArrayList<>();
                DecimateParameters decimateParameters = new DecimateParameters();
                decimate(halfEdgeScenes, decimatedScenes, decimateParameters);
                halfEdgeScenes.set(0, decimatedScenes.get(0)); // provisionally, only one scene.***

                // restore the camera position and target.***
                camera.setPosition(keepCameraPosition);
                camera.setDirection(keepCameraDirection);
                camera.setUp(keepCameraUp);
                camera.setDirty(true);

                gaiaScenesContainer.setCamera(camera);
                gaiaScenesContainer.getProjection().setProjectionType(0);
            }

            if (key == GLFW_KEY_P && action == GLFW_RELEASE) {
                // pyramid deformation.***
                // keep the camera position and target.***
                Vector3d keepCameraPosition = new Vector3d(camera.getPosition());
                Vector3d keepCameraDirection = new Vector3d(camera.getDirection());
                Vector3d keepCameraUp = new Vector3d(camera.getUp());

                // make a depthMap and normalMap.***

                // do pyramidDeformation.***
                GaiaScene gaiaScene = gaiaScenes.get(0);
                GaiaBoundingBox bbox = gaiaScene.getBoundingBox(); // before to set the transformMatrix.***
                double minH = bbox.getMinZ();
                double maxH = bbox.getMaxZ() * 1.1;
                double dist = 6.0;
                GaiaSceneUtils.deformSceneByVerticesConvexity(gaiaScene, dist, minH, maxH);

                // now, update the renderableScene.***
                InternDataConverter internDataConverter = new InternDataConverter();
                RenderableGaiaScene renderableScene = InternDataConverter.getRenderableGaiaScene(gaiaScene);
                this.getGaiaScenesContainer().getRenderableGaiaScenes().set(0, renderableScene);

                // restore the camera position and target.***
                camera.setPosition(keepCameraPosition);
                camera.setDirection(keepCameraDirection);
                camera.setUp(keepCameraUp);
                camera.setDirty(true);

                gaiaScenesContainer.setCamera(camera);
                gaiaScenesContainer.getProjection().setProjectionType(0);
            }

            if (key == GLFW_KEY_E && action == GLFW_RELEASE) {
                // Eliminate the background color.***
                // keep the camera position and target.***
                Vector3d keepCameraPosition = new Vector3d(camera.getPosition());
                Vector3d keepCameraDirection = new Vector3d(camera.getDirection());
                Vector3d keepCameraUp = new Vector3d(camera.getUp());

                // make a depthMap and normalMap.***

                // do pyramidDeformation.***
                GaiaScene gaiaScene = gaiaScenes.get(0);
                GaiaBoundingBox bbox = gaiaScene.getBoundingBox(); // before to set the transformMatrix.***
                double bboxMaxSize = bbox.getMaxSize();

                List<HalfEdgeScene> resultHalfEdgeScenes = new ArrayList<>();
                double pixelsForMeter = 128.0 / bboxMaxSize;
                DecimateParameters decimateParameters = new DecimateParameters();
                decimateParameters.setBasicValues(10.0, 1.0, 3.0, 10.0, 1000000, 1, 0.5);
                this.makeNetSurfaces_TEST(gaiaScenes, resultHalfEdgeScenes, decimateParameters, pixelsForMeter);

                HalfEdgeScene halfEdgeSceneResult = resultHalfEdgeScenes.get(0);
                this.getHalfEdgeScenes().clear();
                this.getHalfEdgeScenes().add(halfEdgeSceneResult);

                GaiaScene gaiaSceneResult = HalfEdgeUtils.gaiaSceneFromHalfEdgeScene(halfEdgeSceneResult);

                // now, update the renderableScene.***
                InternDataConverter internDataConverter = new InternDataConverter();
                RenderableGaiaScene renderableScene = InternDataConverter.getRenderableGaiaScene(gaiaSceneResult);
                this.getGaiaScenesContainer().getRenderableGaiaScenes().set(0, renderableScene);

                // restore the camera position and target.***
                camera.setPosition(keepCameraPosition);
                camera.setDirection(keepCameraDirection);
                camera.setUp(keepCameraUp);
                camera.setDirty(true);

                gaiaScenesContainer.setCamera(camera);
                gaiaScenesContainer.getProjection().setProjectionType(0);
            }

            float rotationOffset = 0.1f;
            Vector3d pivot = new Vector3d(0.0d, 0.0d, -1.0d);
            if (key == GLFW_KEY_C && action == GLFW_RELEASE) {
                int colorType = renderer.getColorMode();
                colorType++;
                if (colorType > 2) {
                    colorType = 0;
                }
                renderer.setColorMode(colorType);

                colorType = halfEdgeRenderer.getColorMode();
                colorType++;
                if (colorType > 2) {
                    colorType = 0;
                }
                halfEdgeRenderer.setColorMode(colorType);
            }
            if (key == GLFW_KEY_W && action == GLFW_RELEASE) {
                //camera.rotationOrbit(0, -rotationOffset, pivot);
                //camera.moveFront(-0.1f);
                this.halfEdgeRenderer.setRenderWireFrame(!this.halfEdgeRenderer.isRenderWireFrame());
                this.renderer.setRenderWireFrame(!this.renderer.isRenderWireFrame());
            }
            if (key == GLFW_KEY_A && action == GLFW_RELEASE) {
                camera.rotationOrbit(rotationOffset, 0, pivot);
            }
            if (key == GLFW_KEY_S && action == GLFW_RELEASE) {
                camera.rotationOrbit(0, rotationOffset, pivot);
            }
            if (key == GLFW_KEY_D && action == GLFW_RELEASE) {
                camera.rotationOrbit(-rotationOffset, 0, pivot);
            }
        });

        if (this.shaderManager == null) {
            setupShader();
        }

        if (renderer == null) {
            renderer = new RenderEngine();
        }

        if (halfEdgeRenderer == null) {
            halfEdgeRenderer = new HalfEdgeRenderer();
        }

        if (camera == null) {
            camera = new Camera();
        }

        if (selectionColorManager == null) {
            selectionColorManager = new SelectionColorManager();
        }

        if (fboManager == null) {
            fboManager = new FboManager();
        }

        if (screenQuad == null) {
            screenQuad = new ScreenQuad();
        }

        if (gaiaScenesContainer == null) {
            int windowWidth = window.getWidth();
            int windowHeight = window.getHeight();
            gaiaScenesContainer = new GaiaScenesContainer(windowWidth, windowHeight);
        }
        gaiaScenesContainer.setCamera(camera);
    }

    public void getDepthRender_TEST(GaiaScene gaiaScene, int bufferedImageType, List<BufferedImage> resultImages, int maxDepthScreenSize) {
        // render the scene
        log.info("Rendering the scene...getDepthRender");

        int screenWidth = 1000; // no used var.***
        int screenHeight = 600; // no used var.***

        GaiaScenesContainer gaiaScenesContainer = this.getGaiaScenesContainer();

        // calculate the projectionMatrix for the camera.***
        GaiaBoundingBox bbox = gaiaScene.getBoundingBox();
        Vector3d bboxCenter = bbox.getCenter();
        float xLength = (float) bbox.getSizeX();
        float yLength = (float) bbox.getSizeY();
        float zLength = (float) bbox.getSizeZ();

//        Vector3d translation = new Vector3d(-bboxCenter.x, -bboxCenter.y, -bboxCenter.z);
//        gaiaScene.getNodes().get(0).translate(translation);

        Projection projection = new Projection(0, screenWidth, screenHeight);
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidthDepth = maxDepthScreenSize;
        int fboHeightDepth = maxDepthScreenSize;
        if (xLength > yLength) {
            fboWidthDepth = maxDepthScreenSize;
            fboHeightDepth = (int) (maxDepthScreenSize * yLength / xLength);
        } else {
            fboWidthDepth = (int) (maxDepthScreenSize * xLength / yLength);
            fboHeightDepth = maxDepthScreenSize;
        }

        fboWidthDepth = Math.max(fboWidthDepth, 1);
        fboHeightDepth = Math.max(fboHeightDepth, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRender2", fboWidthDepth, fboHeightDepth);
        Fbo depthFbo = fboManager.getOrCreateFbo("depthRender", fboWidthDepth, fboHeightDepth);

        // now set camera position.***
        Camera camera = this.camera;
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(0, 0, -1));
        camera.setUp(new Vector3d(0, 1, 0));
        gaiaScenesContainer.setCamera(camera);

        // render the scenes.***
        int scenesCount = 1;
        InternDataConverter internDataConverter = new InternDataConverter();
        RenderableGaiaScene renderableScene = InternDataConverter.getRenderableGaiaScene(gaiaScene);

        gaiaScenesContainer.getRenderableGaiaScenes().clear();
        gaiaScenesContainer.getRenderableGaiaScenes().add(renderableScene);

        int[] width = new int[1];
        int[] height = new int[1];

        try {
            // shader program.***
            ShaderManager shaderManager = this.getShaderManager();
            ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");

            // render the scene.***
            // Bind the fbo.***
            width[0] = colorFbo.getFboWidth();
            height[0] = colorFbo.getFboHeight();

            glViewport(0, 0, width[0], height[0]);
            colorFbo.bind();
            //log.info("Rendering the scene : " + i + " of scenesCount : " + scenesCount);
            this.getRenderSceneImage(sceneShaderProgram);
            colorFbo.unbind();

            // render the scene.***
            // depth render.***
            width[0] = depthFbo.getFboWidth();
            height[0] = depthFbo.getFboHeight();

            glViewport(0, 0, width[0], height[0]);
            ShaderProgram depthShaderProgram = shaderManager.getShaderProgram("depth");
            depthFbo.bind();

            glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // disable cull face.***
            glEnable(GL_DEPTH_TEST);

            // disable cull face.***
            glDisable(GL_CULL_FACE);

            log.info("Rendering the depth : " + 0 + " of scenesCount : " + scenesCount);
            this.getRenderSceneImage(depthShaderProgram);
            depthFbo.unbind();

        } catch (Exception e) {
            log.error("Error initializing the engine: ", e);
        }

//        // delete renderableGaiaScenes.***
//        for(RenderableGaiaScene renderableSceneToDelete : renderableGaiaScenes) {
//            renderableSceneToDelete.deleteGLBuffers();
//        }

        // take the final rendered depthBuffer of the fbo.***
        int depthBufferedImageType = BufferedImage.TYPE_INT_ARGB;
        depthFbo.bind();
        BufferedImage depthImage = depthFbo.getBufferedImage(depthBufferedImageType);
        resultImages.add(depthImage);
        depthFbo.unbind();

        // take the final rendered colorBuffer of the fbo.***
        colorFbo.bind();
        BufferedImage image = colorFbo.getBufferedImage(bufferedImageType);
        resultImages.add(image);
        colorFbo.unbind();
    }

    public void makeNetSurfaces_TEST(List<GaiaScene> scenes, List<HalfEdgeScene> resultHalfEdgeScenes, DecimateParameters decimateParameters, double pixelsForMeter) {

        int scenesCount = scenes.size();
        for (int i = 0; i < scenesCount; i++) {
            GaiaScene gaiaScene = scenes.get(i);
            GaiaBoundingBox bbox = gaiaScene.getBoundingBox();
            double bboxMaxSize = Math.max(bbox.getSizeX(), bbox.getSizeY());
            int maxDepthScreenSize = (int) Math.ceil(pixelsForMeter * bboxMaxSize);
            if (maxDepthScreenSize < 8) {
                maxDepthScreenSize = 8;
            }

            if (maxDepthScreenSize > 1024) {
                maxDepthScreenSize = 1024;
            }

            log.info("Engine.makeNetSurfaces() : maxDepthScreenSize = " + maxDepthScreenSize);

            List<BufferedImage> depthRenderedImages = new ArrayList<>();
            getDepthRender_TEST(gaiaScene, BufferedImage.TYPE_INT_ARGB, depthRenderedImages, maxDepthScreenSize);

            BufferedImage depthRenderedImage = depthRenderedImages.get(0);
            BufferedImage colorRenderedImage = depthRenderedImages.get(1);

//            // save depthRenderedImage as png.***************************************************************************
//            String tempFolderPath = "D:\\Result_mago3dTiler\\temp";
//            String depthRenderedImagePath = tempFolderPath + "\\depthRenderedImage_" + i + ".png";
//            // create the folder.***
//            File tempFolder = new File(tempFolderPath);
//            if(!tempFolder.exists())
//            {
//                tempFolder.mkdirs();
//            }
//            try {
//                ImageIO.write(depthRenderedImage, "png", new File(depthRenderedImagePath));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            String colorRenderedImagePath = tempFolderPath + "\\colorRenderedImage_" + i + ".png";
//            try {
//                ImageIO.write(colorRenderedImage, "png", new File(colorRenderedImagePath));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            // end save depthRenderedImage as png.-----------------------------------------------------------------------

            // make the netSurface by using the depthRenderedImage.***
            float[][] depthValues = com.gaia3d.util.ImageUtils.bufferedImageToFloatMatrix(depthRenderedImage);
            int numCols = depthRenderedImage.getWidth();
            int numRows = depthRenderedImage.getHeight();
            HalfEdgeScene halfEdgeScene = HalfEdgeUtils.getHalfEdgeSceneRectangularNet(numCols, numRows, depthValues, bbox);
            if (halfEdgeScene == null) {
                return;
            }
            halfEdgeScene.setOriginalPath(gaiaScene.getOriginalPath());

            // decimate.***
            halfEdgeScene.doTrianglesReductionOneIteration(decimateParameters);

            // now, cut the halfEdgeScene and make cube-textures by rendering.***
            double gridSpacing = 50.0;
            HalfEdgeOctree resultOctree = new HalfEdgeOctree(null);
            log.info("[Tile][PhotoRealistic][Decimate] Engine.decimate() : cutHalfEdgeSceneGridXYZ.");
            HalfEdgeScene cuttedScene = HalfEdgeCutter.cutHalfEdgeSceneGridXYZ(halfEdgeScene, gridSpacing, resultOctree);
            cuttedScene.splitFacesByBestPlanesToProject();

            // now make box textures for the cuttedScene.***
            log.info("[Tile][PhotoRealistic][Decimate] Engine.decimate() : makeBoxTexturesForHalfEdgeScene.");
            this.makeBoxTexturesForHalfEdgeScene(cuttedScene);

            resultHalfEdgeScenes.add(cuttedScene);
            halfEdgeScene.deleteObjects();
        }


    }

    public void decimate(List<HalfEdgeScene> halfEdgeScenesToDecimate, List<HalfEdgeScene> resultHalfEdgeScenes, DecimateParameters decimateParameters) {
        log.info("[Tile][PhotoRealistic][Decimate] Engine.decimate() : halfEdgeScenesToDecimate count : " + halfEdgeScenesToDecimate.size());
        // do an iteration of decimation.***
        int halfEdgeScenesCount = halfEdgeScenesToDecimate.size();
        for (int i = 0; i < halfEdgeScenesCount; i++) {
            HalfEdgeScene halfEdgeScene = halfEdgeScenesToDecimate.get(i);
            GaiaBoundingBox bbox = halfEdgeScene.getBoundingBox();
            double bboxMaxSize = bbox.getMaxSize();
            halfEdgeScene.doTrianglesReductionOneIteration(decimateParameters);

            // now, cut the halfEdgeScene and make cube-textures by rendering.***
            double gridSpacing = bboxMaxSize / 5.0;
            HalfEdgeOctree resultOctree = new HalfEdgeOctree(null);
            log.info("[Tile][PhotoRealistic][Decimate] Engine.decimate() : cutHalfEdgeSceneGridXYZ.");
            HalfEdgeScene cuttedScene = HalfEdgeCutter.cutHalfEdgeSceneGridXYZ(halfEdgeScene, gridSpacing, resultOctree);
            cuttedScene.splitFacesByBestPlanesToProject();

            // now make box textures for the cuttedScene.***
            log.info("[Tile][PhotoRealistic][Decimate] Engine.decimate() : makeBoxTexturesForHalfEdgeScene.");
            makeBoxTexturesForHalfEdgeScene(cuttedScene);

            resultHalfEdgeScenes.add(cuttedScene);
        }
    }

    public BufferedImage eliminateBackGroundColor(BufferedImage originalImage) {
        if (originalImage == null)
            return null;

        int fboWidth = originalImage.getWidth();
        int fboHeight = originalImage.getHeight();
        if (fboWidth <= 0 || fboHeight <= 0)
            return null;

        try {
            Fbo fbo = fboManager.getOrCreateFbo("default", fboWidth, fboHeight);
            fbo.bind();

            int[] width = new int[1];
            int[] height = new int[1];
            width[0] = fbo.getFboWidth();
            height[0] = fbo.getFboHeight();

            glViewport(0, 0, width[0], height[0]);
            glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
            glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            glDisable(GL20.GL_DEPTH_TEST);

            // enable cull face.***
            glEnable(GL20.GL_CULL_FACE);

            int minFilter = GL20.GL_NEAREST; // GL_LINEAR, GL_NEAREST
            int magFilter = GL20.GL_NEAREST;
            int wrapS = GL20.GL_REPEAT; // GL_CLAMP_TO_EDGE
            int wrapT = GL20.GL_REPEAT;
            int iterationsCount = 10;
            BufferedImage image = originalImage;
            boolean resizeToPowerOf2 = false;

            GL20.glEnable(GL20.GL_TEXTURE_2D);
            GL20.glActiveTexture(GL20.GL_TEXTURE0);

            // shader program.***
            ShaderManager shaderManager = getShaderManager();
            ShaderProgram shaderProgram = shaderManager.getShaderProgram("eliminateBackGroundColor");

            shaderProgram.bind();
            // set uniforms.***
            UniformsMap uniformsMap = shaderProgram.getUniformsMap();
            uniformsMap.setUniform1i("uTexture", 0);
            uniformsMap.setUniform1f("uScreenWidth", (float) fboWidth);
            uniformsMap.setUniform1f("uScreenHeight", (float) fboHeight);
            uniformsMap.setUniform3fv("uBackgroundColor", new Vector3f(0.5f, 0.5f, 0.5f));

            int bufferedImageType = BufferedImage.TYPE_INT_ARGB;

            for (int i = 0; i < iterationsCount; i++) {
                // create the texture.***
                int textureId = RenderableTexturesUtils.createGlTextureFromBufferedImage(image, minFilter, magFilter, wrapS, wrapT, resizeToPowerOf2);
                GL20.glBindTexture(GL20.GL_TEXTURE_2D, textureId);

                screenQuad.render();

                // make the bufferImage.***
                image = fbo.getBufferedImage(bufferedImageType);

                // delete the texture.***
                GL20.glDeleteTextures(textureId);
            }

            fbo.unbind();
            shaderProgram.unbind();

            // return depth test.***
            glEnable(GL20.GL_DEPTH_TEST);

            return image;
        } catch (Exception e) {
            log.error("Error initializing the engine : ", e);
        }

        return null;
    }

    public void makeBoxTexturesForHalfEdgeScene(HalfEdgeScene halfEdgeScene) {
        // Must know all faces classification ids.***
        // 1rst, extract all surfaces.***
        List<HalfEdgeSurface> surfaces = halfEdgeScene.extractSurfaces(null);
        Map<Integer, List<HalfEdgeFace>> facesClassificationMap = new HashMap<>();
        int surfacesCount = surfaces.size();
        for (int i = 0; i < surfacesCount; i++) {
            HalfEdgeSurface surface = surfaces.get(i);
            int facesCount = surface.getFaces().size();
            for (int j = 0; j < facesCount; j++) {
                HalfEdgeFace face = surface.getFaces().get(j);
                int classificationId = face.getClassifyId();
                List<HalfEdgeFace> facesList = facesClassificationMap.computeIfAbsent(classificationId, k -> new ArrayList<>());
                facesList.add(face);
            }
        }

        List<TexturesAtlasData> texturesAtlasDataList = new ArrayList<>();

        Map<Integer, Map<PlaneType, List<HalfEdgeFace>>> mapClassificationPlaneTypeFacesList = new HashMap<>();
        for (Map.Entry<Integer, List<HalfEdgeFace>> entry : facesClassificationMap.entrySet()) {
            int classificationId = entry.getKey();
            List<HalfEdgeFace> facesList = entry.getValue();
            Map<PlaneType, List<HalfEdgeFace>> mapPlaneTypeFacesList = HalfEdgeUtils.makeMapPlaneTypeFacesList(facesList, null);
            mapClassificationPlaneTypeFacesList.put(classificationId, mapPlaneTypeFacesList);

            // now, set projection matrix as orthographic, and set camera's position and target.***
            // calculate the projectionMatrix for the camera.***
            int maxScreenSize = boxRenderingMaxSize;
            // ZNeg texture : plane XYPos.***
            List<HalfEdgeFace> facesPlaneXYPos = mapPlaneTypeFacesList.get(PlaneType.XY);
            if (facesPlaneXYPos != null && !facesPlaneXYPos.isEmpty()) {
                GaiaBoundingBox bboxXYPos = HalfEdgeUtils.getBoundingBoxOfFaces(facesPlaneXYPos);
                BufferedImage ZNegImage = makeZNegTexture(bboxXYPos, maxScreenSize);
                ZNegImage = eliminateBackGroundColor(ZNegImage);

//                // test save images.***
//                try {
//                    ImageIO.write(ZNegImage, "jpg", new File("D:\\Result_mago3dTiler\\ZNegImage" + classificationId + ".jpg"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                if (ZNegImage != null) {
                    TexturesAtlasData texturesAtlasDataZNeg = new TexturesAtlasData();
                    texturesAtlasDataZNeg.setClassifyId(classificationId);
                    texturesAtlasDataZNeg.setPlaneType(PlaneType.XY);
                    texturesAtlasDataZNeg.setTextureImage(ZNegImage);
                    texturesAtlasDataZNeg.setFaceGroupBBox(bboxXYPos);
                    texturesAtlasDataList.add(texturesAtlasDataZNeg);
                } else {
                    // set texCoords as (0.0,0.0).***
                    List<HalfEdgeVertex> vertices = HalfEdgeUtils.getVerticesOfFaces(facesPlaneXYPos, null);
                    for (HalfEdgeVertex vertex : vertices) {
                        if (vertex.getTexcoords() != null) {
                            vertex.getTexcoords().set(0.0, 0.0);
                        }
                    }

                }
            }

            // YPos texture : plane XZNeg.***
            List<HalfEdgeFace> facesPlaneXZNeg = mapPlaneTypeFacesList.get(PlaneType.XZNEG);
            if (facesPlaneXZNeg != null && !facesPlaneXZNeg.isEmpty()) {
                GaiaBoundingBox bboxXZNeg = HalfEdgeUtils.getBoundingBoxOfFaces(facesPlaneXZNeg);
                BufferedImage YPosImage = makeYPosTexture(bboxXZNeg, maxScreenSize);
                YPosImage = eliminateBackGroundColor(YPosImage);

//                // test save images.***
//                try {
//                    ImageIO.write(YPosImage, "jpeg", new File("D:\\Result_mago3dTiler\\YPosImage" + classificationId + ".jpg"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

                if (YPosImage != null) {
                    TexturesAtlasData texturesAtlasDataYPos = new TexturesAtlasData();
                    texturesAtlasDataYPos.setClassifyId(classificationId);
                    texturesAtlasDataYPos.setPlaneType(PlaneType.XZNEG);
                    texturesAtlasDataYPos.setTextureImage(YPosImage);
                    texturesAtlasDataYPos.setFaceGroupBBox(bboxXZNeg);
                    texturesAtlasDataList.add(texturesAtlasDataYPos);
                } else {
                    // set texCoords as (0.0,0.0).***
                    List<HalfEdgeVertex> vertices = HalfEdgeUtils.getVerticesOfFaces(facesPlaneXZNeg, null);
                    for (HalfEdgeVertex vertex : vertices) {
                        if (vertex.getTexcoords() != null) {
                            vertex.getTexcoords().set(0.0, 0.0);
                        }
                    }

                }
            }

            // XPos texture : plane YZNeg.***
            List<HalfEdgeFace> facesPlaneYZNeg = mapPlaneTypeFacesList.get(PlaneType.YZNEG);
            if (facesPlaneYZNeg != null && !facesPlaneYZNeg.isEmpty()) {
                GaiaBoundingBox bboxYZNeg = HalfEdgeUtils.getBoundingBoxOfFaces(facesPlaneYZNeg);
                BufferedImage XPosImage = makeXPosTexture(bboxYZNeg, maxScreenSize);
                XPosImage = eliminateBackGroundColor(XPosImage);

//                // test save images.***
//                try {
//                    ImageIO.write(XPosImage, "jpeg", new File("D:\\Result_mago3dTiler\\XPosImage" + classificationId + ".jpg"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                if (XPosImage != null) {
                    TexturesAtlasData texturesAtlasDataXPos = new TexturesAtlasData();
                    texturesAtlasDataXPos.setClassifyId(classificationId);
                    texturesAtlasDataXPos.setPlaneType(PlaneType.YZNEG);
                    texturesAtlasDataXPos.setTextureImage(XPosImage);
                    texturesAtlasDataXPos.setFaceGroupBBox(bboxYZNeg);
                    texturesAtlasDataList.add(texturesAtlasDataXPos);
                } else {
                    // set texCoords as (0.0,0.0).***
                    List<HalfEdgeVertex> vertices = HalfEdgeUtils.getVerticesOfFaces(facesPlaneYZNeg, null);
                    for (HalfEdgeVertex vertex : vertices) {
                        if (vertex.getTexcoords() != null) {
                            vertex.getTexcoords().set(0.0, 0.0);
                        }
                    }

                }
            }

            // YNeg texture : plane XZPos.***
            List<HalfEdgeFace> facesPlaneXZPos = mapPlaneTypeFacesList.get(PlaneType.XZ);
            if (facesPlaneXZPos != null && !facesPlaneXZPos.isEmpty()) {
                GaiaBoundingBox bboxXZPos = HalfEdgeUtils.getBoundingBoxOfFaces(facesPlaneXZPos);
                BufferedImage YNegImage = makeYNegTexture(bboxXZPos, maxScreenSize);
                YNegImage = eliminateBackGroundColor(YNegImage);

//                // test save images.***
//                try {
//                    ImageIO.write(YNegImage, "jpeg", new File("D:\\Result_mago3dTiler\\YNegImage" + classificationId + ".jpg"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                if (YNegImage != null) {
                    TexturesAtlasData texturesAtlasDataYNeg = new TexturesAtlasData();
                    texturesAtlasDataYNeg.setClassifyId(classificationId);
                    texturesAtlasDataYNeg.setPlaneType(PlaneType.XZ);
                    texturesAtlasDataYNeg.setTextureImage(YNegImage);
                    texturesAtlasDataYNeg.setFaceGroupBBox(bboxXZPos);
                    texturesAtlasDataList.add(texturesAtlasDataYNeg);
                } else {
                    // set texCoords as (0.0,0.0).***
                    List<HalfEdgeVertex> vertices = HalfEdgeUtils.getVerticesOfFaces(facesPlaneXZPos, null);
                    for (HalfEdgeVertex vertex : vertices) {
                        if (vertex.getTexcoords() != null) {
                            vertex.getTexcoords().set(0.0, 0.0);
                        }
                    }

                }
            }

            // XNeg texture : plane YZPos.***
            List<HalfEdgeFace> facesPlaneYZPos = mapPlaneTypeFacesList.get(PlaneType.YZ);
            if (facesPlaneYZPos != null && !facesPlaneYZPos.isEmpty()) {
                GaiaBoundingBox bboxYZPos = HalfEdgeUtils.getBoundingBoxOfFaces(facesPlaneYZPos);
                BufferedImage XNegImage = makeXNegTexture(bboxYZPos, maxScreenSize);
                XNegImage = eliminateBackGroundColor(XNegImage);

//                // test save images.***
//                try {
//                    ImageIO.write(XNegImage, "jpeg", new File("D:\\Result_mago3dTiler\\XNegImage" + classificationId + ".jpg"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                if (XNegImage != null) {
                    TexturesAtlasData texturesAtlasDataXNeg = new TexturesAtlasData();
                    texturesAtlasDataXNeg.setClassifyId(classificationId);
                    texturesAtlasDataXNeg.setPlaneType(PlaneType.YZ);
                    texturesAtlasDataXNeg.setTextureImage(XNegImage);
                    texturesAtlasDataXNeg.setFaceGroupBBox(bboxYZPos);
                    texturesAtlasDataList.add(texturesAtlasDataXNeg);
                } else {
                    // set texCoords as (0.0,0.0).***
                    List<HalfEdgeVertex> vertices = HalfEdgeUtils.getVerticesOfFaces(facesPlaneYZPos, null);
                    for (HalfEdgeVertex vertex : vertices) {
                        if (vertex.getTexcoords() != null) {
                            vertex.getTexcoords().set(0.0, 0.0);
                        }
                    }

                }
            }


//            // provisionally, save the images.***
//            String path = "D:\\temp2";
//            String fileName = "boxTexture_" + classificationId;
//            String extension = ".jpg";
//            String ZNegImagePath = path + "\\" + fileName + "_ZNeg" + extension;
//            String YPosImagePath = path + "\\" + fileName + "_YPos" + extension;
//            String XPosImagePath = path + "\\" + fileName + "_XPos" + extension;
//            String YNegImagePath = path + "\\" + fileName + "_YNeg" + extension;
//            String XNegImagePath = path + "\\" + fileName + "_XNeg" + extension;
//
//
//            try {
//                File ZNegFile = new File(ZNegImagePath);
//                File YPosFile = new File(YPosImagePath);
//                File XPosFile = new File(XPosImagePath);
//                File YNegFile = new File(YNegImagePath);
//                File XNegFile = new File(XNegImagePath);
//
//                log.info("boxTextures : save images.");
//                ImageIO.write(ZNegImage, "jpg", ZNegFile);
//                ImageIO.write(YPosImage, "jpg", YPosFile);
//                ImageIO.write(XPosImage, "jpg", XPosFile);
//                ImageIO.write(YNegImage, "jpg", YNegFile);
//                ImageIO.write(XNegImage, "jpg", XNegFile);
//            } catch (IOException e) {
//                log.error("Error writing image: {}", e);
//            }

        }

        doAtlasTextureProcess(halfEdgeScene, texturesAtlasDataList);
        recalculateTexCoordsAfterTextureAtlasing(halfEdgeScene, texturesAtlasDataList, mapClassificationPlaneTypeFacesList);
        String originalPath = halfEdgeScene.getOriginalPath().toString();

        // extract the originalProjectName from the originalPath.***
        String originalProjectName = originalPath.substring(originalPath.lastIndexOf(File.separator) + 1);
        String rawProjectName = originalProjectName.substring(0, originalProjectName.lastIndexOf("."));

        // make tempFolder if no exists.***
        String tempFolderPath = this.getTempFolderPath();
        File tempFolder = new File(tempFolderPath);
        if (!tempFolder.exists()) {
            tempFolder.mkdirs();
        }
        String fileName = rawProjectName + "_atlas";
        String extension = ".jpg";
        GaiaTexture atlasTexture = makeAtlasTexture(texturesAtlasDataList);
        if(atlasTexture == null) {
            log.info("makeAtlasTexture() : atlasTexture is null.");
            return;
        }
        atlasTexture.setPath(fileName + extension);
        atlasTexture.setParentPath(this.getTempFolderPath());
        String atlasImagePath = atlasTexture.getParentPath() + File.separator + atlasTexture.getPath();
        try {
            File atlasFile = new File(atlasImagePath);
            log.info("[Engine] write atlas image : {}", atlasFile.getAbsoluteFile());
            ImageIO.write(atlasTexture.getBufferedImage(), "jpg", atlasFile);
        } catch (IOException e) {
            log.error("Error writing image: {}", e);
        }

        // finally make material with texture for the halfEdgeScene.***
        GaiaMaterial material = new GaiaMaterial();
        material.setName("atlasTexturesMaterial");
        Map<TextureType, List<GaiaTexture>> textures = new HashMap<>();
        List<GaiaTexture> atlasTextures = new ArrayList<>();
        atlasTextures.add(atlasTexture);
        textures.put(TextureType.DIFFUSE, atlasTextures);
        material.setTextures(textures);

        int materialsCount = halfEdgeScene.getMaterials().size();
        material.setId(materialsCount);
        halfEdgeScene.getMaterials().add(material);

        List<HalfEdgePrimitive> primitives = new ArrayList<>();
        halfEdgeScene.extractPrimitives(primitives);
        for (HalfEdgePrimitive primitive : primitives) {
            primitive.setMaterialId(materialsCount);
        }
    }

    private int getMaxWidth(List<TexturesAtlasData> compareImages) {
        return compareImages.stream().mapToInt(textureScissorData -> (int) textureScissorData.getBatchedBoundary().getMaxX()).max().orElse(0);
    }

    private int getMaxHeight(List<TexturesAtlasData> compareImages) {
        return compareImages.stream().mapToInt(textureScissorData -> (int) textureScissorData.getBatchedBoundary().getMaxY()).max().orElse(0);
    }

    private GaiaTexture makeAtlasTexture(List<TexturesAtlasData> texAtlasDatasList) {
        int imageType = BufferedImage.TYPE_INT_RGB;

        int maxWidth = getMaxWidth(texAtlasDatasList);
        int maxHeight = getMaxHeight(texAtlasDatasList);

        if(maxWidth == 0 || maxHeight == 0) {
            log.error("makeAtlasTexture() : maxWidth or maxHeight is 0.");
            return null;
        }

        GaiaTexture textureAtlas = new GaiaTexture();
        log.debug("Atlas maxWidth : {}, maxHeight : {}", maxWidth, maxHeight);
        textureAtlas.createImage(maxWidth, maxHeight, imageType);

        // draw the images into textureAtlas.***
        log.debug("HalfEdgeSurface.scissorTextures() : draw the images into textureAtlas.");
        Graphics2D g2d = textureAtlas.getBufferedImage().createGraphics();
        int textureAtlasDatasCount = texAtlasDatasList.size();
        for (int i = 0; i < textureAtlasDatasCount; i++) {
            TexturesAtlasData textureAtlasData = texAtlasDatasList.get(i);
            GaiaRectangle currentBoundary = textureAtlasData.getCurrentBoundary();
            GaiaRectangle batchedBoundary = textureAtlasData.getBatchedBoundary();
            GaiaRectangle originBoundary = textureAtlasData.getOriginalBoundary();

            BufferedImage subImage = textureAtlasData.getTextureImage();

//            // test random color for each splitImage.***************************************************************************************************
//            Color randomColor = new Color((float) Math.random(), (float) Math.random(), (float) Math.random(), 0.8f);
//            BufferedImage randomColoredImage = new BufferedImage(subImage.getWidth(), subImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
//            Graphics2D randomGraphics = randomColoredImage.createGraphics();
//            randomGraphics.setColor(randomColor);
//            randomGraphics.fillRect(0, 0, subImage.getWidth(), subImage.getHeight());
//            randomGraphics.dispose();
//            g2d.drawImage(randomColoredImage, (int) batchedBoundary.getMinX(), (int) batchedBoundary.getMinY(), null); // test code.***
//            // end test.--------------------------------------------------------------------------------------------------------------------------------

            g2d.drawImage(subImage, (int) batchedBoundary.getMinX(), (int) batchedBoundary.getMinY(), null); // original code.***

        }
        g2d.dispose();

        return textureAtlas;
    }

    private void recalculateTexCoordsAfterTextureAtlasing(HalfEdgeScene halfEdgeScene, List<TexturesAtlasData> texAtlasDatasList,
                                                          Map<Integer, Map<PlaneType, List<HalfEdgeFace>>> mapClassificationPlaneTypeFacesList) {
        int maxWidth = getMaxWidth(texAtlasDatasList);
        int maxHeight = getMaxHeight(texAtlasDatasList);

        if (maxWidth == 0 || maxHeight == 0) {
            return;
        }

        Map<HalfEdgeVertex, HalfEdgeVertex> groupVertexMapMemSave = new HashMap<>();
        Map<HalfEdgeVertex, HalfEdgeVertex> visitedVertexMapMemSave = new HashMap<>();
        visitedVertexMapMemSave.clear();

        List<HalfEdgeVertex> faceVerticesMemSave = new ArrayList<>();

        int texAtlasDatasCount = texAtlasDatasList.size();
        for (int i = 0; i < texAtlasDatasCount; i++) {
            TexturesAtlasData texAtlasData = texAtlasDatasList.get(i);
            int classifyId = texAtlasData.getClassifyId();
            PlaneType planeType = texAtlasData.getPlaneType();
            List<HalfEdgeFace> faceGroup = mapClassificationPlaneTypeFacesList.get(classifyId).get(planeType);
            GaiaBoundingBox faceGroupBBox = texAtlasData.getFaceGroupBBox();

            GaiaRectangle currentBoundary = texAtlasData.getCurrentBoundary();
            GaiaRectangle originalBoundary = texAtlasData.getOriginalBoundary();
            GaiaRectangle batchedBoundary = texAtlasData.getBatchedBoundary();

            // calculate the faceGroup projected boundary.***
            GaiaRectangle faceGroupProjectedBoundary = new GaiaRectangle(0.0, 0.0, 0.0, 0.0);
            if (planeType == PlaneType.XY) {
                faceGroupProjectedBoundary.setSize(faceGroupBBox.getMinX(), faceGroupBBox.getMinY(), faceGroupBBox.getMaxX(), faceGroupBBox.getMaxY());
            } else if (planeType == PlaneType.XZNEG) {
                faceGroupProjectedBoundary.setSize(faceGroupBBox.getMinX(), faceGroupBBox.getMinZ(), faceGroupBBox.getMaxX(), faceGroupBBox.getMaxZ());
            } else if (planeType == PlaneType.YZNEG) {
                faceGroupProjectedBoundary.setSize(faceGroupBBox.getMinY(), faceGroupBBox.getMinZ(), faceGroupBBox.getMaxY(), faceGroupBBox.getMaxZ());
            } else if (planeType == PlaneType.XZ) {
                faceGroupProjectedBoundary.setSize(faceGroupBBox.getMinX(), faceGroupBBox.getMinZ(), faceGroupBBox.getMaxX(), faceGroupBBox.getMaxZ());
            } else if (planeType == PlaneType.YZ) {
                faceGroupProjectedBoundary.setSize(faceGroupBBox.getMinY(), faceGroupBBox.getMinZ(), faceGroupBBox.getMaxY(), faceGroupBBox.getMaxZ());
            }


            double texWidth = texAtlasData.getTextureImage().getWidth();
            double texHeight = texAtlasData.getTextureImage().getHeight();
            double xPixelSize = 1.0 / texWidth;
            double yPixelSize = 1.0 / texHeight;

            // obtain all vertex of the faceGroup.***
            groupVertexMapMemSave.clear();
            int facesCount = faceGroup.size();
            for (int j = 0; j < facesCount; j++) {
                HalfEdgeFace face = faceGroup.get(j);
                faceVerticesMemSave.clear();
                faceVerticesMemSave = face.getVertices(faceVerticesMemSave);
                int verticesCount = faceVerticesMemSave.size();
                for (int k = 0; k < verticesCount; k++) {
                    HalfEdgeVertex vertex = faceVerticesMemSave.get(k);
                    groupVertexMapMemSave.put(vertex, vertex);
                }
            }

            // now, calculate the vertex list from the map.***
            List<HalfEdgeVertex> vertexList = new ArrayList<>(groupVertexMapMemSave.values());

            double faceGroupWidth = faceGroupProjectedBoundary.getWidth();
            double faceGroupHeight = faceGroupProjectedBoundary.getHeight();
            int verticesCount = vertexList.size();
            for (int k = 0; k < verticesCount; k++) {
                HalfEdgeVertex vertex = vertexList.get(k);

                // calculate the real texCoords.***
                Vector2d texCoord = vertex.getTexcoords();
                Vector3d position = vertex.getPosition();
                double posX = position.x;
                double posY = position.y;
                if (planeType == PlaneType.XY) {
                    posX = position.x;
                    posY = position.y;
                } else if (planeType == PlaneType.XZNEG) {
                    posX = position.x;
                    posY = position.z;
                } else if (planeType == PlaneType.YZNEG) {
                    posX = position.y;
                    posY = position.z;
                } else if (planeType == PlaneType.XZ) {
                    posX = position.x;
                    posY = position.z;
                } else if (planeType == PlaneType.YZ) {
                    posX = position.y;
                    posY = position.z;
                }

                double x = (posX - faceGroupProjectedBoundary.getMinX()) / faceGroupWidth;
                double y = (posY - faceGroupProjectedBoundary.getMinY()) / faceGroupHeight;

                // invert y.***
                y = 1.0 - y;

                if (planeType == PlaneType.YZNEG) {
                    // invert x.***
                    x = 1.0 - x;
                } else if (planeType == PlaneType.XZ) {
                    // invert x.***
                    x = 1.0 - x;
                }

                double pixelX = x * texWidth;
                double pixelY = y * texHeight;

                // transform the texCoords to texCoordRelToCurrentBoundary.***
                double xRel = (pixelX - originalBoundary.getMinX()) / originalBoundary.getWidthInt();
                double yRel = (pixelY - originalBoundary.getMinY()) / originalBoundary.getHeightInt();

                // clamp the texRelCoords.***
                xRel = Math.max(0.0 + xPixelSize, Math.min(1.0 - xPixelSize, xRel));
                yRel = Math.max(0.0 + yPixelSize, Math.min(1.0 - yPixelSize, yRel));

                // transform the texCoordRelToCurrentBoundary to atlasBoundary using batchedBoundary.***
                double xAtlas = (batchedBoundary.getMinX() + xRel * batchedBoundary.getWidthInt()) / maxWidth;
                double yAtlas = (batchedBoundary.getMinY() + yRel * batchedBoundary.getHeightInt()) / maxHeight;

                texCoord.set(xAtlas, yAtlas);
                vertex.setTexcoords(texCoord);
            }

        }

    }

    private void doAtlasTextureProcess(HalfEdgeScene halfEdgeScene, List<TexturesAtlasData> texAtlasDatasList) {
        // 1rst, sort the texAtlasData by width and height.***
        List<TexturesAtlasData> texAtlasDataWidther = new ArrayList<>();
        List<TexturesAtlasData> texAtlasDataHigher = new ArrayList<>();
        int texAtlasDataCount = texAtlasDatasList.size();
        for (int i = 0; i < texAtlasDataCount; i++) {
            TexturesAtlasData texAtlasDataAux = texAtlasDatasList.get(i);
            BufferedImage textureImage = texAtlasDataAux.getTextureImage();
            int classifyId = texAtlasDataAux.getClassifyId();
            PlaneType planeType = texAtlasDataAux.getPlaneType();
            GaiaRectangle originalBoundary = texAtlasDataAux.getOriginalBoundary();

            double w = originalBoundary.getWidth();
            double h = originalBoundary.getHeight();
            if (w > h) {
                texAtlasDataWidther.add(texAtlasDataAux);
            } else {
                texAtlasDataHigher.add(texAtlasDataAux);
            }
        }

        // now, sort each list by width and height.***
        texAtlasDataWidther.sort((o1, o2) -> {
            GaiaRectangle originalBoundary1 = o1.getOriginalBoundary();
            GaiaRectangle originalBoundary2 = o2.getOriginalBoundary();
            double w1 = originalBoundary1.getWidth();
            double w2 = originalBoundary2.getWidth();
            return Double.compare(w2, w1);
        });

        texAtlasDataHigher.sort((o1, o2) -> {
            GaiaRectangle originalBoundary1 = o1.getOriginalBoundary();
            GaiaRectangle originalBoundary2 = o2.getOriginalBoundary();
            double h1 = originalBoundary1.getHeight();
            double h2 = originalBoundary2.getHeight();
            return Double.compare(h2, h1);
        });

        // make an unique atlasDataList alternating the texAtlasDataWidther and texAtlasDataHigher.***
        texAtlasDatasList.clear();
        int texAtlasDataWidtherCount = texAtlasDataWidther.size();
        int texAtlasDataHigherCount = texAtlasDataHigher.size();
        int texAtlasDataMaxCount = Math.max(texAtlasDataWidtherCount, texAtlasDataHigherCount);
        for (int i = 0; i < texAtlasDataMaxCount; i++) {
            if (i < texAtlasDataWidtherCount) {
                texAtlasDatasList.add(texAtlasDataWidther.get(i));
            }
            if (i < texAtlasDataHigherCount) {
                texAtlasDatasList.add(texAtlasDataHigher.get(i));
            }
        }

        // now, make the atlas texture.***************************************************************************************
        GaiaRectangle beforeMosaicRectangle = new GaiaRectangle(0.0, 0.0, 0.0, 0.0);
        List<GaiaRectangle> list_rectangles = new ArrayList<>();

        TreeMap<Double, List<GaiaRectangle>> map_maxXrectangles = new TreeMap<>();

        Vector2d bestPosition = new Vector2d();
        List<TexturesAtlasData> currProcessTextureAtlasDates = new ArrayList<>();
        texAtlasDataCount = texAtlasDatasList.size();
        for (int i = 0; i < texAtlasDataCount; i++) {
            TexturesAtlasData texAtlasDataAux = texAtlasDatasList.get(i);
            GaiaRectangle originBoundary = texAtlasDataAux.getOriginalBoundary();

            GaiaRectangle batchedBoundary = null;
            if (i == 0) {
                // the 1rst textureScissorData.***
                batchedBoundary = new GaiaRectangle(0.0, 0.0, originBoundary.getWidthInt(), originBoundary.getHeightInt());
                texAtlasDataAux.setBatchedBoundary(batchedBoundary);
                beforeMosaicRectangle.copyFrom(batchedBoundary);
            } else {
                // 1rst, find the best position for image into atlas.***
                bestPosition = this.getBestPositionMosaicInAtlas(currProcessTextureAtlasDates, texAtlasDataAux, bestPosition, beforeMosaicRectangle, list_rectangles, map_maxXrectangles);
                batchedBoundary = new GaiaRectangle(bestPosition.x, bestPosition.y, bestPosition.x + originBoundary.getWidthInt(), bestPosition.y + originBoundary.getHeightInt());
                texAtlasDataAux.setBatchedBoundary(batchedBoundary);
                beforeMosaicRectangle.addBoundingRectangle(batchedBoundary);
            }

            list_rectangles.add(batchedBoundary);
            currProcessTextureAtlasDates.add(texAtlasDataAux);

            // map.************************************************************************************************************************
            double maxX = batchedBoundary.getMaxX();

            List<GaiaRectangle> list_rectanglesMaxX = map_maxXrectangles.computeIfAbsent(maxX, k -> new ArrayList<>());
            list_rectanglesMaxX.add(batchedBoundary);
        }
    }

    private Vector2d getBestPositionMosaicInAtlas(List<TexturesAtlasData> currProcessTextureAtlasDates, TexturesAtlasData texAtlasData_toPutInMosaic, Vector2d resultVec,
                                                  GaiaRectangle beforeMosaicRectangle, List<GaiaRectangle> list_rectangles, TreeMap<Double, List<GaiaRectangle>> map_maxXrectangles) {
        if (resultVec == null) {
            resultVec = new Vector2d();
        }

        double currPosX, currPosY;
        double candidatePosX = 0.0, candidatePosY = 0.0;
        double currMosaicPerimeter, candidateMosaicPerimeter;
        candidateMosaicPerimeter = -1.0;
        double error = 1.0 - 1e-6;

        // Now, try to find the best positions to put our rectangle.***
        int existentTexAtlasDatasCount = currProcessTextureAtlasDates.size();
        for (int i = 0; i < existentTexAtlasDatasCount; i++) {
            TexturesAtlasData existentTexAtlasData = currProcessTextureAtlasDates.get(i);
            GaiaRectangle currRect = existentTexAtlasData.getBatchedBoundary();

            // for each existent rectangles, there are 2 possibles positions: leftUp & rightDown.***
            // in this 2 possibles positions we put our leftDownCorner of rectangle of "splitData_toPutInMosaic".***

            // If in some of two positions our rectangle intersects with any other rectangle, then discard.***
            // If no intersects with others rectangles, then calculate the mosaic-perimeter.
            // We choose the minor perimeter of the mosaic.***

            double width = texAtlasData_toPutInMosaic.getOriginalBoundary().getWidthInt();
            double height = texAtlasData_toPutInMosaic.getOriginalBoundary().getHeightInt();

            // 1- leftUp corner.***
            currPosX = currRect.getMinX();
            currPosY = currRect.getMaxY();

            // setup our rectangle.***
            if (texAtlasData_toPutInMosaic.getBatchedBoundary() == null) {
                texAtlasData_toPutInMosaic.setBatchedBoundary(new GaiaRectangle(0.0, 0.0, 0.0, 0.0));
            }
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMinX(currPosX);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMinY(currPosY);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMaxX(currPosX + width);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMaxY(currPosY + height);

            // put our rectangle into mosaic & check that no intersects with another rectangles.***
            if (!this.intersectsRectangleAtlasingProcess(list_rectangles, texAtlasData_toPutInMosaic.getBatchedBoundary(), map_maxXrectangles)) {
                GaiaRectangle afterMosaicRectangle = new GaiaRectangle(0.0, 0.0, 0.0, 0.0);
                afterMosaicRectangle.copyFrom(beforeMosaicRectangle);
                afterMosaicRectangle.addBoundingRectangle(texAtlasData_toPutInMosaic.getBatchedBoundary());

                // calculate the perimeter of the mosaic.***
                if (candidateMosaicPerimeter < 0.0) {
                    candidateMosaicPerimeter = afterMosaicRectangle.getPerimeter();
                    candidatePosX = currPosX;
                    candidatePosY = currPosY;
                } else {
                    currMosaicPerimeter = afterMosaicRectangle.getPerimeter();
                    if (candidateMosaicPerimeter >= currMosaicPerimeter * error) {
                        candidateMosaicPerimeter = currMosaicPerimeter;
                        candidatePosX = currPosX;
                        candidatePosY = currPosY;
                        break; // test delete.*****************************
                    }
                }
            }

            // 2- rightDown corner.***
            currPosX = currRect.getMaxX();
            currPosY = currRect.getMinY();

            // setup our rectangle.***
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMinX(currPosX);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMinY(currPosY);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMaxX(currPosX + width);
            texAtlasData_toPutInMosaic.getBatchedBoundary().setMaxY(currPosY + height);

            // put our rectangle into mosaic & check that no intersects with another rectangles.***
            if (!this.intersectsRectangleAtlasingProcess(list_rectangles, texAtlasData_toPutInMosaic.getBatchedBoundary(), map_maxXrectangles)) {
                GaiaRectangle afterMosaicRectangle = new GaiaRectangle(0.0, 0.0, 0.0, 0.0);
                afterMosaicRectangle.copyFrom(beforeMosaicRectangle);
                afterMosaicRectangle.addBoundingRectangle(texAtlasData_toPutInMosaic.getBatchedBoundary());

                // calculate the perimeter of the mosaic.***
                if (candidateMosaicPerimeter < 0.0) {
                    candidateMosaicPerimeter = afterMosaicRectangle.getPerimeter();
                    candidatePosX = currPosX;
                    candidatePosY = currPosY;
                } else {
                    currMosaicPerimeter = afterMosaicRectangle.getPerimeter();
                    if (candidateMosaicPerimeter >= currMosaicPerimeter * error) {
                        candidateMosaicPerimeter = currMosaicPerimeter;
                        candidatePosX = currPosX;
                        candidatePosY = currPosY;
                        break; // test delete.*****************************
                    }
                }
            }
        }

        resultVec.set(candidatePosX, candidatePosY);

        return resultVec;
    }

    private boolean intersectsRectangleAtlasingProcess(List<GaiaRectangle> listRectangles, GaiaRectangle rectangle, TreeMap<Double, List<GaiaRectangle>> map_maxXrectangles) {
        // this function returns true if the rectangle intersects with any existent rectangle of the listRectangles.***
        boolean intersects = false;
        double error = 10E-5;

        double currRectMinX = rectangle.getMinX();

        // check with map_maxXrectangles all rectangles that have maxX > currRectMinX.***
        for (Map.Entry<Double, List<GaiaRectangle>> entry : map_maxXrectangles.tailMap(currRectMinX).entrySet()) {
            List<GaiaRectangle> existentRectangles = entry.getValue();

            int existentRectanglesCount = existentRectangles.size();
            for (int i = 0; i < existentRectanglesCount; i++) {
                GaiaRectangle existentRectangle = existentRectangles.get(i);
                if (existentRectangle == rectangle) {
                    continue;
                }
                if (existentRectangle.intersects(rectangle, error)) {
                    return true;
                }
            }
        }


//        for (GaiaRectangle existentRectangle : listRectangles) {
//            if (existentRectangle == rectangle) {
//                continue;
//            }
//            if (existentRectangle.intersects(rectangle, error)) {
//                intersects = true;
//                break;
//            }
//        }
        return intersects;
    }

    private BufferedImage getRenderedImageBoxTextures(Fbo fbo, ShaderProgram shaderProgram) {
        // render the renderableScene.***
        try {
            fbo.bind();

            int[] width = new int[1];
            int[] height = new int[1];
            width[0] = fbo.getFboWidth();
            height[0] = fbo.getFboHeight();

            glViewport(0, 0, width[0], height[0]);
            glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glEnable(GL_DEPTH_TEST);

            // enable cull face.***
            glEnable(GL_CULL_FACE);

            // shader program.***
            ShaderManager shaderManager = getShaderManager();
            ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");

            // render the scene.***
            getRenderSceneImage(sceneShaderProgram);

            // make the bufferImage.***
            int bufferedImageType = BufferedImage.TYPE_INT_RGB;
            BufferedImage image = fbo.getBufferedImage(bufferedImageType);

            fbo.unbind();

            return image;
        } catch (Exception e) {
            log.error("Error initializing the engine : ", e);
        }

        return null;
    }


    private BufferedImage makeXNegTexture(GaiaBoundingBox bbox, int maxScreenSize) {
        // x positive texture.***
        Vector3d bboxCenter = bbox.getCenter(); // center of the bbox.***
        float xLength = (float) bbox.getSizeY(); // attention : xLength is yLength.***
        float yLength = (float) bbox.getSizeZ(); // attention : yLength is zLength.***
        float zLength = (float) bbox.getSizeX(); // attention : zLength is xLength.***

        Projection projection = gaiaScenesContainer.getProjection();
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidth = maxScreenSize;
        int fboHeight = maxScreenSize;
        if (xLength > yLength) {
            fboWidth = maxScreenSize;
            fboHeight = (int) (maxScreenSize * yLength / xLength);
        } else {
            fboWidth = (int) (maxScreenSize * xLength / yLength);
            fboHeight = maxScreenSize;
        }

        fboWidth = Math.max(fboWidth, 1);
        fboHeight = Math.max(fboHeight, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRenderBoxTex", fboWidth, fboHeight);

        // now set camera position.***
        Camera camera = gaiaScenesContainer.getCamera();
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(-1, 0, 0)); // x positive.***
        camera.setUp(new Vector3d(0, 0, 1));
        gaiaScenesContainer.setCamera(camera);

        // render the renderableScene.***
        // shader program.***
        ShaderManager shaderManager = getShaderManager();
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");
        BufferedImage image = getRenderedImageBoxTextures(colorFbo, sceneShaderProgram);
        fboManager.deleteFbo("colorRenderBoxTex");
        return image;
    }

    private BufferedImage makeXPosTexture(GaiaBoundingBox bbox, int maxScreenSize) {
        // x positive texture.***
        Vector3d bboxCenter = bbox.getCenter(); // center of the bbox.***
        float xLength = (float) bbox.getSizeY(); // attention : xLength is yLength.***
        float yLength = (float) bbox.getSizeZ(); // attention : yLength is zLength.***
        float zLength = (float) bbox.getSizeX(); // attention : zLength is xLength.***

        Projection projection = gaiaScenesContainer.getProjection();
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidth = maxScreenSize;
        int fboHeight = maxScreenSize;
        if (xLength > yLength) {
            fboWidth = maxScreenSize;
            fboHeight = (int) (maxScreenSize * yLength / xLength);
        } else {
            fboWidth = (int) (maxScreenSize * xLength / yLength);
            fboHeight = maxScreenSize;
        }

        fboWidth = Math.max(fboWidth, 1);
        fboHeight = Math.max(fboHeight, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRenderBoxTex", fboWidth, fboHeight);

        // now set camera position.***
        Camera camera = gaiaScenesContainer.getCamera();
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(1, 0, 0)); // x positive.***
        camera.setUp(new Vector3d(0, 0, 1));
        gaiaScenesContainer.setCamera(camera);

        // render the renderableScene.***
        // shader program.***
        ShaderManager shaderManager = getShaderManager();
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");
        BufferedImage image = getRenderedImageBoxTextures(colorFbo, sceneShaderProgram);
        fboManager.deleteFbo("colorRenderBoxTex");
        return image;
    }

    private BufferedImage makeYNegTexture(GaiaBoundingBox bbox, int maxScreenSize) {
        // y negative texture.***
        Vector3d bboxCenter = bbox.getCenter();
        float xLength = (float) bbox.getSizeX();
        float yLength = (float) bbox.getSizeZ(); // attention : yLength is zLength.***
        float zLength = (float) bbox.getSizeY(); // attention : zLength is yLength.***

        Projection projection = gaiaScenesContainer.getProjection();
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidth = maxScreenSize;
        int fboHeight = maxScreenSize;
        if (xLength > yLength) {
            fboWidth = maxScreenSize;
            fboHeight = (int) (maxScreenSize * yLength / xLength);
        } else {
            fboWidth = (int) (maxScreenSize * xLength / yLength);
            fboHeight = maxScreenSize;
        }

        fboWidth = Math.max(fboWidth, 1);
        fboHeight = Math.max(fboHeight, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRenderBoxTex", fboWidth, fboHeight);

        // now set camera position.***
        Camera camera = gaiaScenesContainer.getCamera();
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(0, -1, 0)); // y negative.***
        camera.setUp(new Vector3d(0, 0, 1));
        gaiaScenesContainer.setCamera(camera);

        // render the renderableScene.***
        // shader program.***
        ShaderManager shaderManager = getShaderManager();
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");
        BufferedImage image = getRenderedImageBoxTextures(colorFbo, sceneShaderProgram);
        fboManager.deleteFbo("colorRenderBoxTex");
        return image;
    }

    private BufferedImage makeYPosTexture(GaiaBoundingBox bbox, int maxScreenSize) {
        // y positive texture.***
        Vector3d bboxCenter = bbox.getCenter();
        float xLength = (float) bbox.getSizeX();
        float yLength = (float) bbox.getSizeZ(); // attention : yLength is zLength.***
        float zLength = (float) bbox.getSizeY(); // attention : zLength is yLength.***

        Projection projection = gaiaScenesContainer.getProjection();
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidth = maxScreenSize;
        int fboHeight = maxScreenSize;
        if (xLength > yLength) {
            fboWidth = maxScreenSize;
            fboHeight = (int) (maxScreenSize * yLength / xLength);
        } else {
            fboWidth = (int) (maxScreenSize * xLength / yLength);
            fboHeight = maxScreenSize;
        }

        fboWidth = Math.max(fboWidth, 1);
        fboHeight = Math.max(fboHeight, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRenderBoxTex", fboWidth, fboHeight);

        // now set camera position.***
        Camera camera = gaiaScenesContainer.getCamera();
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(0, 1, 0)); // y positive.***
        camera.setUp(new Vector3d(0, 0, 1));
        gaiaScenesContainer.setCamera(camera);

        // render the renderableScene.***
        // shader program.***
        ShaderManager shaderManager = getShaderManager();
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");
        BufferedImage image = getRenderedImageBoxTextures(colorFbo, sceneShaderProgram);
        fboManager.deleteFbo("colorRenderBoxTex");
        return image;
    }

    private BufferedImage makeZNegTexture(GaiaBoundingBox bbox, int maxScreenSize) {
        // z negative texture.***
        Vector3d bboxCenter = bbox.getCenter();
        float xLength = (float) bbox.getSizeX();
        float yLength = (float) bbox.getSizeY();
        float zLength = (float) bbox.getSizeZ();

        Projection projection = gaiaScenesContainer.getProjection();
        projection.setProjectionOrthographic(-xLength / 2.0f, xLength / 2.0f, -yLength / 2.0f, yLength / 2.0f, -zLength * 0.5f, zLength * 0.5f);
        gaiaScenesContainer.setProjection(projection);

        // Take FboManager from engine.***
        FboManager fboManager = this.getFboManager();

        // create the fbo.***
        int fboWidth = maxScreenSize;
        int fboHeight = maxScreenSize;
        if (xLength > yLength) {
            fboWidth = maxScreenSize;
            fboHeight = (int) (maxScreenSize * yLength / xLength);
        } else {
            fboWidth = (int) (maxScreenSize * xLength / yLength);
            fboHeight = maxScreenSize;
        }

        fboWidth = Math.max(fboWidth, 1);
        fboHeight = Math.max(fboHeight, 1);

        Fbo colorFbo = fboManager.getOrCreateFbo("colorRenderBoxTex", fboWidth, fboHeight);

        // now set camera position.***
        Camera camera = gaiaScenesContainer.getCamera();
        camera.setPosition(bboxCenter);
        camera.setDirection(new Vector3d(0, 0, -1));
        camera.setUp(new Vector3d(0, 1, 0));
        gaiaScenesContainer.setCamera(camera);

        // render the renderableScene.***
        // shader program.***
        ShaderManager shaderManager = getShaderManager();
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");
        BufferedImage image = getRenderedImageBoxTextures(colorFbo, sceneShaderProgram);
        fboManager.deleteFbo("colorRenderBoxTex");
        return image;
    }

    private String readResource(String resourceLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(resourceLocation);
        byte[] bytes = null;
        try {
            bytes = resourceAsStream.readAllBytes();
        } catch (IOException e) {
            log.error("Error reading resource: {}", e);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void setupShader() {
        shaderManager = new ShaderManager();

        GL.createCapabilities();

        URL url = getClass().getClassLoader().getResource("shaders");
        File shaderFolder = new File(url.getPath());

        log.info("shaderFolder: {}", shaderFolder.getAbsolutePath());

        String vertexShaderText = readResource("shaders/sceneV330.vert");
        String fragmentShaderText = readResource("shaders/sceneV330.frag");


//        log.info("vertexShaderText: {}", vertexShaderText);
//        log.info("fragmentShaderText: {}", fragmentShaderText);


        // create a scene shader program.*************************************************************************************************
        java.util.List<ShaderProgram.ShaderModuleData> shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(vertexShaderText, GL20.GL_VERTEX_SHADER));
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(fragmentShaderText, GL20.GL_FRAGMENT_SHADER));
        ShaderProgram sceneShaderProgram = shaderManager.createShaderProgram("scene", shaderModuleDataList);


        java.util.List<String> uniformNames = new ArrayList<>();
        uniformNames.add("uProjectionMatrix");
        uniformNames.add("uModelViewMatrix");
        uniformNames.add("uObjectMatrix");
        uniformNames.add("texture0");
        uniformNames.add("uColorMode");
        uniformNames.add("uOneColor");
        sceneShaderProgram.createUniforms(uniformNames);
        sceneShaderProgram.validate();


        // create depthShader.****************************************************************************************************
        vertexShaderText = readResource("shaders/depthV330.vert");
        fragmentShaderText = readResource("shaders/depthV330.frag");

        shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(vertexShaderText, GL20.GL_VERTEX_SHADER));
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(fragmentShaderText, GL20.GL_FRAGMENT_SHADER));
        sceneShaderProgram = shaderManager.createShaderProgram("depth", shaderModuleDataList);

        uniformNames = new ArrayList<>();
        uniformNames.add("uProjectionMatrix");
        uniformNames.add("uModelViewMatrix");
        uniformNames.add("uObjectMatrix");
        sceneShaderProgram.createUniforms(uniformNames);
        sceneShaderProgram.validate();

        // create a screen shader program.******************************************************************************************
        vertexShaderText = readResource("shaders/screenV330.vert");
        fragmentShaderText = readResource("shaders/screenV330.frag");

        shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(vertexShaderText, GL20.GL_VERTEX_SHADER));
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(fragmentShaderText, GL20.GL_FRAGMENT_SHADER));
        ShaderProgram screenShaderProgram = shaderManager.createShaderProgram("screen", shaderModuleDataList);

        uniformNames = new ArrayList<>();
        uniformNames.add("texture0");
        screenShaderProgram.createUniforms(uniformNames);
        screenShaderProgram.validate();

        // create eliminateBackGroundColor shader.******************************************************************************************
        vertexShaderText = readResource("shaders/eliminateBackGroundColorV330.vert");
        fragmentShaderText = readResource("shaders/eliminateBackGroundColorV330.frag");

        shaderModuleDataList = new ArrayList<>();
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(vertexShaderText, GL20.GL_VERTEX_SHADER));
        shaderModuleDataList.add(new ShaderProgram.ShaderModuleData(fragmentShaderText, GL20.GL_FRAGMENT_SHADER));
        ShaderProgram eliminateBackGroundColorShaderProgram = shaderManager.createShaderProgram("eliminateBackGroundColor", shaderModuleDataList);

        uniformNames = new ArrayList<>();
        uniformNames.add("uScreenWidth");
        uniformNames.add("uScreenHeight");
        uniformNames.add("texture0");
        uniformNames.add("uBackgroundColor");
        eliminateBackGroundColorShaderProgram.createUniforms(uniformNames);
        eliminateBackGroundColorShaderProgram.validate();
    }

    private void takeColorCodedPhoto(RenderableGaiaScene renderableGaiaScene, Fbo fbo, ShaderProgram shaderProgram) {
        fbo.bind();
        glViewport(0, 0, fbo.getFboWidth(), fbo.getFboHeight()); // 500 x 500
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        // render scene objects.***
        shaderProgram.bind();

        Camera camera = gaiaScenesContainer.getCamera();
        Matrix4d modelViewMatrix = camera.getModelViewMatrix();
        UniformsMap uniformsMap = shaderProgram.getUniformsMap();
        uniformsMap.setUniformMatrix4fv("uModelViewMatrix", new Matrix4f(modelViewMatrix));

        // disable cull face.***
        glDisable(GL_CULL_FACE);
        renderer.renderColorCoded(renderableGaiaScene, selectionColorManager, shaderProgram);
        shaderProgram.unbind();

        fbo.unbind();

        // return the viewport to window size.***
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        glViewport(0, 0, windowWidth, windowHeight);
    }

    private void determineExteriorAndInteriorObjects(Fbo fbo) {
        // bind the fbo.***
        fbo.bind();

        // read pixels from fbo.***
        int fboWidth = fbo.getFboWidth();
        int fboHeight = fbo.getFboHeight();
        ByteBuffer pixels = ByteBuffer.allocateDirect(fboWidth * fboHeight * 4);
        glReadPixels(0, 0, fboWidth, fboHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        // unbind the fbo.***
        fbo.unbind();

        // determine exterior and interior objects.***
        int pixelsCount = fboWidth * fboHeight;
        for (int i = 0; i < pixelsCount; i++) {
            int colorCode = pixels.getInt(i * 4);
            // background color is (1, 1, 1, 1). skip background color.***
            if (colorCode == 0xFFFFFFFF) {
                continue;
            }
            RenderablePrimitive renderablePrimitive = (RenderablePrimitive) selectionColorManager.mapColorRenderable.get(colorCode);
            if (renderablePrimitive != null) {
                // determine exterior or interior.***
                // 0 = interior, 1 = exterior, -1 = unknown.***
                renderablePrimitive.setStatus(1);
            }
        }
    }

    private RenderableGaiaScene processExteriorInterior(GaiaScene gaiaScene) {
        RenderableGaiaScene renderableGaiaScene = InternDataConverter.getRenderableGaiaScene(gaiaScene);
        gaiaScenesContainer.addRenderableGaiaScene(renderableGaiaScene);
        GaiaBoundingBox bbox = gaiaScene.getBoundingBox();
        float maxLength = (float) bbox.getLongestDistance();
        float bboxHight = (float) bbox.getMaxZ() - (float) bbox.getMinZ();
        float semiMaxLength = maxLength / 2.0f;
        semiMaxLength *= 150.0f;

        // render into frame buffer.***
        Fbo colorRenderFbo = fboManager.getFbo("colorCodeRender");

        // render scene objects.***
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("colorCode");
        sceneShaderProgram.bind();
        Matrix4f projectionOrthoMatrix = new Matrix4f().ortho(-semiMaxLength, semiMaxLength, -semiMaxLength, semiMaxLength, -semiMaxLength * 10.0f, semiMaxLength * 10.0f);

        // make colorRenderableMap.***
        java.util.List<RenderablePrimitive> allRenderablePrimitives = new ArrayList<>();
        renderableGaiaScene.extractRenderablePrimitives(allRenderablePrimitives);
        int renderablePrimitivesCount = allRenderablePrimitives.size();
        for (int i = 0; i < renderablePrimitivesCount; i++) {
            RenderablePrimitive renderablePrimitive = allRenderablePrimitives.get(i);
            renderablePrimitive.setStatus(0); // init as interior.***
            int colorCode = selectionColorManager.getAvailableColor();
            renderablePrimitive.setColorCode(colorCode);
            selectionColorManager.mapColorRenderable.put(colorCode, renderablePrimitive);
        }

        UniformsMap uniformsMap = sceneShaderProgram.getUniformsMap();
        uniformsMap.setUniformMatrix4fv("uProjectionMatrix", projectionOrthoMatrix);

        // take 8 photos at the top, 8 photos at lateral, 8 photos at the bottom.***
        // top photos.***
        Camera camera = gaiaScenesContainer.getCamera();
        double increAngRad = Math.toRadians(360.0 / 8.0);
        Matrix4d rotMat = new Matrix4d();
        rotMat.rotateZ(increAngRad);
        Vector3d cameraPosition = new Vector3d(0, -semiMaxLength, semiMaxLength);
        Vector3d cameraTarget = new Vector3d(0, 0, 0);

        for (int i = 0; i < 8; i++) {
            // set camera position.***
            camera.calculateCameraXYPlane(cameraPosition, cameraTarget);

            takeColorCodedPhoto(renderableGaiaScene, colorRenderFbo, sceneShaderProgram);
            determineExteriorAndInteriorObjects(colorRenderFbo);

            // rotate camPos.
            rotMat.transformPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z, cameraPosition);
        }

        // lateral photos.***
        cameraPosition = new Vector3d(0, -semiMaxLength, 0);
        cameraTarget = new Vector3d(0, 0, 0);
        for (int i = 0; i < 8; i++) {
            // set camera position.***
            camera.calculateCameraXYPlane(cameraPosition, cameraTarget);

            takeColorCodedPhoto(renderableGaiaScene, colorRenderFbo, sceneShaderProgram);
            determineExteriorAndInteriorObjects(colorRenderFbo);

            // rotate camPos.
            rotMat.transformPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z, cameraPosition);
        }

        // bottom photos.***
        cameraPosition = new Vector3d(0, -semiMaxLength, -semiMaxLength);
        cameraTarget = new Vector3d(0, 0, 0);
        for (int i = 0; i < 8; i++) {
            // set camera position.***
            camera.calculateCameraXYPlane(cameraPosition, cameraTarget);

            takeColorCodedPhoto(renderableGaiaScene, colorRenderFbo, sceneShaderProgram);
            determineExteriorAndInteriorObjects(colorRenderFbo);

            // rotate camPos.
            rotMat.transformPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z, cameraPosition);
        }

        // return camera position.***
        cameraPosition = new Vector3d(0, 0, -semiMaxLength);
        cameraTarget = new Vector3d(0, 0, 0);

        // set camera position.***
        camera.calculateCameraXYPlane(cameraPosition, cameraTarget);


        return renderableGaiaScene;
    }

    public Map<GaiaPrimitive, Integer> getExteriorAndInteriorGaiaPrimitivesMap(GaiaScene gaiaScene, Map<GaiaPrimitive, Integer> mapPrimitiveStatus) {
        RenderableGaiaScene renderableGaiaScene = processExteriorInterior(gaiaScene);

        java.util.List<RenderablePrimitive> allRenderablePrimitives = new ArrayList<>();
        renderableGaiaScene.extractRenderablePrimitives(allRenderablePrimitives);
        int renderablePrimitivesCount = allRenderablePrimitives.size();

        // finally make exteriorGaiaSet & interiorGaiaSet.***
        if (mapPrimitiveStatus == null) {
            mapPrimitiveStatus = new HashMap<>();
        } else {
            mapPrimitiveStatus.clear();
        }

        for (int i = 0; i < renderablePrimitivesCount; i++) {
            RenderablePrimitive renderablePrimitive = allRenderablePrimitives.get(i);
            int status = renderablePrimitive.getStatus();
            if (status == 1) {
                mapPrimitiveStatus.put(renderablePrimitive.getOriginalGaiaPrimitive(), 1);
            } else if (status == 0) {
                mapPrimitiveStatus.put(renderablePrimitive.getOriginalGaiaPrimitive(), 0);
            }
        }

        return mapPrimitiveStatus;
    }

    private void deletePrimitivesByStatus(GaiaNode gaiaNode, int statusToDelete, Map<GaiaPrimitive, Integer> mapPrimitiveStatus) {
        java.util.List<GaiaMesh> gaiaMeshes = gaiaNode.getMeshes();
        int meshesCount = gaiaMeshes.size();
        for (int i = 0; i < meshesCount; i++) {
            GaiaMesh gaiaMesh = gaiaMeshes.get(i);
            java.util.List<GaiaPrimitive> gaiaPrimitives = gaiaMesh.getPrimitives();
            int primitivesCount = gaiaPrimitives.size();
            for (int j = 0; j < primitivesCount; j++) {
                GaiaPrimitive gaiaPrimitive = gaiaPrimitives.get(j);
                int status = mapPrimitiveStatus.get(gaiaPrimitive);
                if (status == statusToDelete) {
                    gaiaPrimitives.remove(j);
                    j--;
                    primitivesCount--;
                }
            }

            // check if the gaiaMesh has no primitives.***
            if (gaiaPrimitives.size() == 0) {
                gaiaMeshes.remove(i);
                i--;
                meshesCount--;
            }
        }

        java.util.List<GaiaNode> children = gaiaNode.getChildren();
        int childrenCount = children.size();
        for (int i = 0; i < childrenCount; i++) {
            GaiaNode child = children.get(i);
            deletePrimitivesByStatus(child, statusToDelete, mapPrimitiveStatus);
        }
    }


    public void getExteriorAndInteriorGaiaScenes(GaiaScene gaiaScene, java.util.List<GaiaScene> resultExteriorGaiaScenes, java.util.List<GaiaScene> resultInteriorGaiaScenes) {
        Map<GaiaPrimitive, Integer> mapPrimitiveStatus = getExteriorAndInteriorGaiaPrimitivesMap(gaiaScene, null);

        // finally make exteriorGaiaSet & interiorGaiaSet.***
        GaiaScene exteriorGaiaScene = gaiaScene.clone();
        GaiaScene interiorGaiaScene = gaiaScene.clone();
        resultExteriorGaiaScenes.add(exteriorGaiaScene);
        resultInteriorGaiaScenes.add(interiorGaiaScene);

        // delete interior primitives from exteriorGaiaScene, and delete exterior primitives from interiorGaiaScene.***
        java.util.List<GaiaNode> exteriorNodes = exteriorGaiaScene.getNodes();
        int extNodesCount = exteriorNodes.size();
        for (int i = 0; i < extNodesCount; i++) {
            GaiaNode gaiaNode = exteriorNodes.get(i);
            deletePrimitivesByStatus(gaiaNode, 0, mapPrimitiveStatus);
        }

        java.util.List<GaiaNode> interiorNodes = interiorGaiaScene.getNodes();
        int intNodesCount = interiorNodes.size();
        for (int i = 0; i < intNodesCount; i++) {
            GaiaNode gaiaNode = interiorNodes.get(i);
            deletePrimitivesByStatus(gaiaNode, 1, mapPrimitiveStatus);
        }
    }

    public void getExteriorAndInteriorGaiaSets(GaiaScene gaiaScene, java.util.List<GaiaSet> resultExteriorGaiaSets, java.util.List<GaiaSet> resultInteriorGaiaSets) {
        RenderableGaiaScene renderableGaiaScene = processExteriorInterior(gaiaScene);

        java.util.List<RenderablePrimitive> allRenderablePrimitives = new ArrayList<>();
        renderableGaiaScene.extractRenderablePrimitives(allRenderablePrimitives);
        int renderablePrimitivesCount = allRenderablePrimitives.size();

        // finally make exteriorGaiaSet & interiorGaiaSet.***
        GaiaSet exteriorGaiaSet = new GaiaSet();
        GaiaSet interiorGaiaSet = new GaiaSet();
        resultExteriorGaiaSets.add(exteriorGaiaSet);
        resultInteriorGaiaSets.add(interiorGaiaSet);
        java.util.List<GaiaBufferDataSet> exteriorBufferDatas = new ArrayList<>();
        List<GaiaBufferDataSet> interiorBufferDatas = new ArrayList<>();
        exteriorGaiaSet.setBufferDataList(exteriorBufferDatas);
        interiorGaiaSet.setBufferDataList(interiorBufferDatas);
        for (int i = 0; i < renderablePrimitivesCount; i++) {
            RenderablePrimitive renderablePrimitive = allRenderablePrimitives.get(i);
            int status = renderablePrimitive.getStatus();
            if (status == 1) {
                GaiaBufferDataSet gaiaBufferDataSet = renderablePrimitive.getOriginalBufferDataSet();
                exteriorBufferDatas.add(gaiaBufferDataSet);
            } else if (status == 0) {
                GaiaBufferDataSet gaiaBufferDataSet = renderablePrimitive.getOriginalBufferDataSet();
                interiorBufferDatas.add(gaiaBufferDataSet);
            }
        }
    }

    private void renderScreenQuad(int texId) {
        // render to windows using screenQuad.***
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        glViewport(0, 0, windowWidth, windowHeight);

        GL20.glEnable(GL20.GL_TEXTURE_2D);
        GL20.glActiveTexture(GL20.GL_TEXTURE0);
        GL20.glBindTexture(GL20.GL_TEXTURE_2D, texId);

        ShaderProgram screenShaderProgram = shaderManager.getShaderProgram("screen");
        screenShaderProgram.bind();
        screenQuad.render();
        screenShaderProgram.unbind();
    }

    private void draw() {
        // render into frame buffer.***
        FboManager fboManager = this.getFboManager();
        Window window = this.getWindow();
        int fboWidthColor = window.getWidth();
        int fboHeightColor = window.getHeight();
        Fbo colorRenderFbo = fboManager.getOrCreateFbo("colorRender", fboWidthColor, fboHeightColor);

        colorRenderFbo.bind();

        int[] width = new int[1];
        int[] height = new int[1];
        width[0] = colorRenderFbo.getFboWidth();
        height[0] = colorRenderFbo.getFboHeight();
        glViewport(0, 0, width[0], height[0]);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // render scene objects.***
        ShaderProgram sceneShaderProgram = shaderManager.getShaderProgram("scene");

        sceneShaderProgram.bind(); // bind the shader program.*****************************************************************************

        // set modelViewMatrix and projectionMatrix.***
        UniformsMap uniformsMap = sceneShaderProgram.getUniformsMap();
        Matrix4f projectionMatrix = gaiaScenesContainer.getProjection().getProjMatrix();

        uniformsMap.setUniformMatrix4fv("uProjectionMatrix", projectionMatrix);
        Camera camera = gaiaScenesContainer.getCamera();
        Matrix4d modelViewMatrix = camera.getModelViewMatrix();
        uniformsMap.setUniformMatrix4fv("uModelViewMatrix", new Matrix4f(modelViewMatrix));

        Matrix4f identityMatrix = new Matrix4f();
        identityMatrix.identity();
        uniformsMap.setUniformMatrix4fv("uObjectMatrix", identityMatrix);

        // colorMode = 0: oneColor, 1: vertexColor, 2: textureColor
        uniformsMap.setUniform1i("uColorMode", 0);
        Vector4f oneColor = new Vector4f(1, 0.5f, 0.25f, 1);
        uniformsMap.setUniform4fv("uOneColor", oneColor);


        if (renderAxis) {
            renderer.renderAxis(sceneShaderProgram);
        }

        if (!gaiaScenesContainer.getRenderableGaiaScenes().isEmpty()) {
            renderer.render(gaiaScenesContainer, sceneShaderProgram);
        }

        if (!this.halfEdgeScenes.isEmpty()) {
            halfEdgeRenderer.renderHalfEdgeScenes(halfEdgeScenes, sceneShaderProgram);
        }
        sceneShaderProgram.unbind(); // unbind the shader program.***************************************************************************

        colorRenderFbo.unbind();

        // now render to windows using screenQuad.***
        int colorRenderTextureId = colorRenderFbo.getColorTextureId();
        renderScreenQuad(colorRenderTextureId);


//        // render colorCoded fbo.***
//        int colorCodeRenderTextureId = fboManager.getFbo("colorCodeRender").getColorTextureId();
//        renderScreenQuad(colorCodeRenderTextureId);

    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.

        int[] width = new int[1];
        int[] height = new int[1];
        long windowHandle = window.getWindowHandle();
        while (!glfwWindowShouldClose(windowHandle)) {

            glfwGetWindowSize(windowHandle, width, height);
            glViewport(0, 0, width[0], height[0]);

            glEnable(GL_DEPTH_TEST);
            glPointSize(5.0f);
            glClearColor(0.5f, 0.23f, 0.98f, 1.0f);
            glClearDepth(1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            draw();
            glfwSwapBuffers(windowHandle);
            glfwPollEvents();
        }
    }

    public void deleteBuffer(int vboId) {
        GL20.glDeleteBuffers(vboId);
    }

}
