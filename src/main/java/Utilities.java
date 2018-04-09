import com.google.common.math.DoubleMath;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.convolution.Convolution;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.inverse.InvertMatrix;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.util.BigDecimalMath;
import static org.nd4j.linalg.convolution.Convolution.Type.SAME;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.CustomOp;

public class Utilities {
  public static int optimizeGridSize(int gridSize,
                                     int xLength) {
    if (gridSize <= 0 || xLength <= 0) {
      throw new IllegalArgumentException("Arguments must be greater than 0.");
    }
    if(gridSize == 200) {
      gridSize = Math.max(xLength, 512);
    }
    gridSize = (int) Math.pow(2, Math.ceil(DoubleMath.log2(gridSize)));
    return gridSize;
  }

  public static INDArray getBins(INDArray x,
                                 INDArray y,
                                 double deltaX,
                                 double deltaY,
                                 Quartet<Double, Double, Double, Double> extents) {
    INDArray bins = Nd4j.vstack(x, y).transpose();
    bins.subiRowVector(Nd4j.create(new double[] {extents.getValue0(), extents.getValue2()}));
    bins.diviRowVector(Nd4j.create(new double[] {deltaX, deltaY}));
    bins = Transforms.floor(bins).transpose();
    return bins;
  }

  public static INDArray getCovariance(INDArray bins,
                                       boolean noCorrelation) {
    INDArray[] covarianceMatrix = PCA.covarianceMatrix(bins);
    INDArray covariance = covarianceMatrix[0];

    if (noCorrelation) {
      covariance.putScalar(new int[] {1,0}, 0.0);
      covariance.putScalar(new int[] {0,1}, 0.0);
    }

    return covariance;
  }

  public static double getScottsFactor(int xLength,
                                       double adjust) {
    return Math.pow(xLength, (-1. / 6.)) * adjust;
  }

  public static INDArray getStandardDeviations(INDArray covariance) {
    return Nd4j.diag(Transforms.sqrt(covariance));
  }

  public static INDArray getKernN(INDArray standardDeviations,
                                  double scottsFactor) {
    return Transforms.round(standardDeviations.mul(scottsFactor * 2 * BigDecimalMath.PI.doubleValue()));
  }

  public static INDArray getBandwidth(INDArray covariance,
                                      double scottsFactor) {
    return InvertMatrix.invert(covariance.mul(Math.pow(scottsFactor, 2)), false);
  }

  public static Pair<INDArray, INDArray> getMeshGrid(INDArray xCoords,
                                                     INDArray yCoords) {
    int numRows = yCoords.length();
    int numCols = xCoords.length();

    xCoords = xCoords.reshape(1, numCols);
    yCoords = yCoords.reshape(numRows, 1);

    INDArray X = xCoords.repeat(0, numRows);
    INDArray Y = yCoords.repeat(1, numCols);

    return new Pair<>(X, Y);
  }

  public static INDArray getKernel(double kernNx,
                                   double kernNy,
                                   INDArray X,
                                   INDArray Y,
                                   INDArray bandwidth) {
    INDArray XFlattened = Nd4j.toFlattened(X);
    INDArray YFlattened = Nd4j.toFlattened(Y);

    INDArray kernel = Nd4j.vstack(XFlattened,
            YFlattened);
    kernel = bandwidth.mmul(kernel).mul(kernel);
    kernel = Nd4j.sum(kernel, 0).div(2.0);
    kernel = Transforms.exp(kernel.mul(-1.0));
    kernel = kernel.reshape((int) kernNy, (int) kernNx);

    return kernel;
  }

  //TODO
  public static INDArray convolveGrid(INDArray grid,
                                      INDArray kernel) {
    return Convolution.conv2d(grid, kernel, SAME);
  }
  //TODO
  public static INDArray getNormalizationFactor(INDArray covariance,
                                                double scottsFactor,
                                                int n,
                                                double deltaX,
                                                double deltaY) {
    INDArray normalizationFactor = covariance.mul(2 * BigDecimalMath.PI.doubleValue()).mul(Math.pow(scottsFactor, 2));

    //TODO : for some reason this returns the correct value but not negative
    INDArray det = Nd4j.create(new double[] {0.});
    CustomOp determinant = DynamicCustomOp.builder("matrix_determinant")
            .addInputs(normalizationFactor)
            .addOutputs(det)
            .build();
    Nd4j.getExecutioner().exec(determinant);
    normalizationFactor = det;

    normalizationFactor = Transforms.sqrt(normalizationFactor).mul(n * deltaX * deltaY);

    return normalizationFactor;
  }
}