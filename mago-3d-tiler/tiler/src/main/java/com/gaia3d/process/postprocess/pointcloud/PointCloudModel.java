package com.gaia3d.process.postprocess.pointcloud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaia3d.basic.geometry.GaiaBoundingBox;
import com.gaia3d.basic.model.GaiaScene;
import com.gaia3d.basic.pointcloud.GaiaPointCloud;
import com.gaia3d.basic.model.GaiaVertex;
import com.gaia3d.command.mago.GlobalOptions;
import com.gaia3d.process.postprocess.TileModel;
import com.gaia3d.process.postprocess.batch.GaiaBatchTable;
import com.gaia3d.process.postprocess.instance.GaiaFeatureTable;
import com.gaia3d.process.tileprocess.tile.ContentInfo;
import com.gaia3d.process.tileprocess.tile.TileInfo;
import com.gaia3d.util.GlobeUtils;
import com.gaia3d.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.locationtech.proj4j.BasicCoordinateTransform;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.InvalidValueException;
import org.locationtech.proj4j.ProjCoordinate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
public class PointCloudModel implements TileModel {
    @Override
    public ContentInfo run(ContentInfo contentInfo) {
        GlobalOptions globalOptions = GlobalOptions.getInstance();
        String featureTableJson;
        String batchTableJson;

        File outputFile = new File(globalOptions.getOutputPath());
        Path outputRoot = outputFile.toPath().resolve("data");
        File outputRootFile = outputRoot.toFile();
        if (!outputRootFile.mkdir() && !outputRootFile.exists()) {
            log.error("Failed to create output directory : {}", outputRoot);
        }
        List<TileInfo> tileInfos = contentInfo.getTileInfos();

        GaiaBoundingBox boundingBox = new GaiaBoundingBox();
        AtomicInteger vertexCount = new AtomicInteger();
        tileInfos.forEach((tileInfo) -> {
            GaiaPointCloud pointCloud = tileInfo.getPointCloud();
            //pointCloud.maximizeTemp();
            //List<GaiaVertex> gaiaVertex = pointCloud.getVertices();
            vertexCount.addAndGet(pointCloud.getVertexCount());
            boundingBox.addBoundingBox(pointCloud.getGaiaBoundingBox());
            //pointCloud.minimizeTemp();
        });

        Vector3d originalMinPosition = boundingBox.getMinPosition();
        Vector3d originalMaxPosition = boundingBox.getMaxPosition();
        CoordinateReferenceSystem source = globalOptions.getCrs();
        BasicCoordinateTransform transformer = new BasicCoordinateTransform(source, GlobeUtils.wgs84);

        ProjCoordinate transformedMinCoordinate = transformer.transform(new ProjCoordinate(originalMinPosition.x, originalMinPosition.y, originalMinPosition.z), new ProjCoordinate());
        Vector3d minPosition = new Vector3d(transformedMinCoordinate.x, transformedMinCoordinate.y, originalMinPosition.z);
        ProjCoordinate transformedMaxCoordinate = transformer.transform(new ProjCoordinate(originalMaxPosition.x, originalMaxPosition.y, originalMaxPosition.z), new ProjCoordinate());
        Vector3d maxPosition = new Vector3d(transformedMaxCoordinate.x, transformedMaxCoordinate.y, originalMaxPosition.z);
        GaiaBoundingBox wgs84BoundingBox = new GaiaBoundingBox();
        wgs84BoundingBox.addPoint(minPosition);
        wgs84BoundingBox.addPoint(maxPosition);

        int vertexLength = vertexCount.get();
        float[] positions = new float[vertexLength * 3];
        int[] quantizedPositions = new int[vertexLength * 3];
        byte[] colors = new byte[vertexLength * 3];
        float[] batchIds = new float[vertexLength];

        if (vertexLength == 1) {
            log.error("Vertex length is 1");
            return contentInfo;
        }

        Vector3d center = wgs84BoundingBox.getCenter();
        Vector3d centerWorldCoordinate = GlobeUtils.geographicToCartesianWgs84(center);
        Matrix4d transformMatrix = GlobeUtils.transformMatrixAtCartesianPointWgs84(centerWorldCoordinate);
        Matrix4d transformMatrixInv = new Matrix4d(transformMatrix).invert();

        Matrix3d rotationMatrix3d = transformMatrix.get3x3(new Matrix3d());
        Matrix3d xRotationMatrix3d = new Matrix3d();
        xRotationMatrix3d.identity();
        xRotationMatrix3d.rotateX(Math.toRadians(-90));
        xRotationMatrix3d.mul(rotationMatrix3d, rotationMatrix3d);
        Matrix4d rotationMatrix4d = new Matrix4d(rotationMatrix3d);

        GaiaBoundingBox quantizedVolume = new GaiaBoundingBox();
        AtomicInteger mainIndex = new AtomicInteger();
        AtomicInteger positionIndex = new AtomicInteger();
        AtomicInteger colorIndex= new AtomicInteger();
        AtomicInteger batchIdIndex= new AtomicInteger();
        tileInfos.forEach((tileInfo) -> {
            GaiaPointCloud pointCloud = tileInfo.getPointCloud();
            pointCloud.maximize();
            List<GaiaVertex> gaiaVertex = pointCloud.getVertices();
            gaiaVertex.forEach((vertex) -> {
                int index = mainIndex.getAndIncrement();
                if (index > vertexLength) {
                    log.error("Index out of bound");
                    return;
                }

                Vector3d position = vertex.getPosition();
                Vector3d wgs84Position = new Vector3d();
                try {
                    ProjCoordinate transformedCoordinate = transformer.transform(new ProjCoordinate(position.x, position.y, position.z), new ProjCoordinate());
                    wgs84Position = new Vector3d(transformedCoordinate.x, transformedCoordinate.y, position.z);
                } catch (InvalidValueException e) {
                    log.debug("Invalid value exception", e);
                }

                Vector3d positionWorldCoordinate = GlobeUtils.geographicToCartesianWgs84(wgs84Position);
                Vector3d localPosition = positionWorldCoordinate.mulPosition(transformMatrixInv, new Vector3d());
                float batchId = vertex.getBatchId();

                localPosition.mulPosition(rotationMatrix4d, localPosition);

                float x = (float) localPosition.x;
                float y = (float) -localPosition.z;
                float z = (float) localPosition.y;
                quantizedVolume.addPoint(new Vector3d(x, y, z));

                positions[positionIndex.getAndIncrement()] = x;
                positions[positionIndex.getAndIncrement()] = y;
                positions[positionIndex.getAndIncrement()] = z;

                byte[] color = vertex.getColor();
                colors[colorIndex.getAndIncrement()] = color[0];
                colors[colorIndex.getAndIncrement()] = color[1];
                colors[colorIndex.getAndIncrement()] = color[2];

                batchIds[batchIdIndex.getAndIncrement()] = batchId;
            });
            pointCloud.minimizeTemp();
        });

        // quantization
        Vector3d quantizationScale = calcQuantizedVolumeScale(quantizedVolume);
        Vector3d quantizationOffset = calcQuantizedVolumeOffset(quantizedVolume);
        for (int i = 0; i < positions.length; i+=3) {
            double x = positions[i];
            double y = positions[i + 1];
            double z = positions[i + 2];
            double xQuantized = (x - quantizationOffset.x) / quantizationScale.x;
            double yQuantized = (y - quantizationOffset.y) / quantizationScale.y;
            double zQuantized = (z - quantizationOffset.z) / quantizationScale.z;

            quantizedPositions[i] = (int) (xQuantized * 65535);
            quantizedPositions[i + 1] = (int) (yQuantized * 65535);
            quantizedPositions[i + 2] = (int) (zQuantized * 65535);

            // Clamp to 16-bit unsigned integer range
            if (quantizedPositions[i] < 0) {
                quantizedPositions[i] = 0;
                log.error("Quantized position x is less than 0");
            } else if (quantizedPositions[i] > 65535) {
                quantizedPositions[i] = 65535;
                log.error("Quantized position x is greater than 65535");
            }

            if (quantizedPositions[i + 1] < 0) {
                quantizedPositions[i + 1] = 0;
                log.error("Quantized position y is less than 0");
            } else if (quantizedPositions[i + 1] > 65535) {
                quantizedPositions[i + 1] = 65535;
                log.error("Quantized position y is greater than 65535");
            }

            if (quantizedPositions[i + 2] < 0) {
                quantizedPositions[i + 2] = 0;
                log.error("Quantized position z is less than 0");
            } else if (quantizedPositions[i + 2] > 65535) {
                quantizedPositions[i + 2] = 65535;
                log.error("Quantized position z is greater than 65535");
            }
        }

        // check quantizationScale, quantizationOffset is NaN or Infinity
        if (Double.isNaN(quantizationScale.x) || Double.isNaN(quantizationScale.y) || Double.isNaN(quantizationScale.z)) {
            log.error("Quantization scale is NaN");
            log.error("Quantization scale : {}", quantizationScale);
            log.error("Quantized volume : {}", quantizedVolume);
        } else if (Double.isInfinite(quantizationScale.x) || Double.isInfinite(quantizationScale.y) || Double.isInfinite(quantizationScale.z)) {
            log.error("Quantization scale is Infinite");
            log.error("Quantization scale : {}", quantizationScale);
            log.error("Quantized volume : {}", quantizedVolume);
        } else if (Double.isNaN(quantizationOffset.x) || Double.isNaN(quantizationOffset.y) || Double.isNaN(quantizationOffset.z)) {
            log.error("Quantization offset is NaN");
            log.error("Quantization offset : {}", quantizationOffset);
            log.error("Quantized volume : {}", quantizedVolume);
        } else if (Double.isInfinite(quantizationOffset.x) || Double.isInfinite(quantizationOffset.y) || Double.isInfinite(quantizationOffset.z)) {
            log.error("Quantization offset is Infinite");
            log.error("Quantization offset : {}", quantizationOffset);
            log.error("Quantized volume : {}", quantizedVolume);
        }

        PointCloudBinary pointCloudBinary = new PointCloudBinary();
        pointCloudBinary.setPositions(quantizedPositions);
        pointCloudBinary.setColors(colors);

        byte[] positionBytes = pointCloudBinary.getPositionBytes();
        byte[] colorBytes = pointCloudBinary.getColorBytes();
        byte[] featureTableBytes = new byte[positionBytes.length + colorBytes.length /*+ batchIdBytes.length*/];
        System.arraycopy(positionBytes, 0, featureTableBytes, 0, positionBytes.length);
        System.arraycopy(colorBytes, 0, featureTableBytes, positionBytes.length, colorBytes.length);

        byte[] batchTableBytes = new byte[0];
        GaiaFeatureTable featureTable = new GaiaFeatureTable();
        featureTable.setPointsLength(vertexLength);
        featureTable.setQuantizedVolumeOffset(new float[]{(float) quantizationOffset.x, (float) quantizationOffset.y, (float) quantizationOffset.z});
        featureTable.setQuantizedVolumeScale(new float[]{(float) quantizationScale.x, (float) quantizationScale.y, (float) quantizationScale.z});
        featureTable.setPositionQuantized(new Position(0));
        featureTable.setColor(new Color(positionBytes.length));

        if (!globalOptions.isClassicTransformMatrix()) {
            double[] rtcCenter = new double[3];
            rtcCenter[0] = transformMatrix.m30();
            rtcCenter[1] = transformMatrix.m31();
            rtcCenter[2] = transformMatrix.m32();
            featureTable.setRctCenter(rtcCenter);
        }
        //featureTable.setBatchLength(1);
        //BatchId batchIdObject = new BatchId(0, "FLOAT");
        //featureTable.setBatchId(batchIdObject);
        GaiaBatchTable batchTable = new GaiaBatchTable();
        //List<String> batchTableIds = batchTable.getBatchId();
        //batchTableIds.add("0");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        try {
            featureTableJson = StringUtils.doPadding8Bytes(objectMapper.writeValueAsString(featureTable));
            batchTableJson = StringUtils.doPadding8Bytes(objectMapper.writeValueAsString(batchTable));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }

        PointCloudBinaryWriter writer = new PointCloudBinaryWriter(featureTableJson, batchTableJson, featureTableBytes, batchTableBytes);
        writer.write(outputRoot, contentInfo.getNodeCode());
        return contentInfo;
    }

    private Vector3d calcQuantizedVolumeOffset(GaiaBoundingBox boundingBox) {
        Vector3d min = boundingBox.getMinPosition();
        return min;
    }

    private Vector3d calcQuantizedVolumeScale(GaiaBoundingBox boundingBox) {
        Vector3d volume = boundingBox.getVolume();
        return volume;
    }
}
