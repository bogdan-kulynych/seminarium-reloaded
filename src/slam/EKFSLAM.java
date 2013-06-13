package slam;

import java.util.HashSet;
import java.util.Set;

import lejos.util.Matrix;
import utils.Angle;
import utils.Point;
import utils.Pose;

public class EKFSLAM {	
	Matrix sigma;
	AugmentedState mu;
	
	Matrix Rt, Qt;
	
	private int N;
	private Set<Integer> seenLandmarks;
	
	private Matrix I;
	private Matrix Fx;
	
	public EKFSLAM(Pose start, Matrix Rt, Matrix Qt, int signaturesCount) {
		// Initializing main matrices
		N = signaturesCount;
		mu = new AugmentedState(N);
		
		mu.set(0, 0, start.position.x);
		mu.set(1, 0, start.position.y);
		mu.set(2, 0, start.direction.deg());
		
		this.Rt = Rt;
		this.Qt = Qt;
		
		seenLandmarks = new HashSet<Integer>();
		
		// Initializing helpers
		I = Matrix.identity(3, 3);
		
		double initial[][] = new double[3][3 + 3*N];
		initial[0][0] = 1;
		initial[1][1] = 1;
		initial[2][2] = 1;
		
		Fx = new Matrix(initial);
	}
	
	public void inform(Point translationDelta, Angle rotationDelta) {
		mu.set(0, 0, mu.get(0, 0) + translationDelta.x);
		mu.set(1, 0, mu.get(0, 0) + translationDelta.x);
		mu.set(2, 0, mu.get(0, 0) + rotationDelta.deg());
		Matrix d = new Matrix(3, 3);
		d.set(0, 2, -translationDelta.y);
		d.set(1, 2, translationDelta.x);
		Matrix Gt = I.plus(Fx.transpose().times(d.times(Fx)));
		sigma = Gt.times(sigma.times(Gt.transpose()))
				.plus(Fx.transpose().times(Rt.times(Fx)));
	}
	
	public void inform(Pose observation, int observedSignature) {
		// Observation is expected to provide dx, dy of observation 
		int j = observedSignature; 
		
		// If landmark never seen before 
		if (!seenLandmarks.add(observedSignature)) {	
			mu.setLandmarkPose(j, 
				(float)(mu.get(0, 0) + observation.position.x),
				(float)(mu.get(1, 0) + observation.position.y)
			);
			seenLandmarks.add(observedSignature);
		}

		Pose landmark = mu.getLandmark(j);
		Pose robot = mu.getRobotPose();
		
		float dx = landmark.position.x - robot.position.x;
		float dy = landmark.position.y - robot.position.y;
		float q = dx*dx + dy*dy;
		double dr = Math.sqrt(q);
		double da = Math.atan2(dy, dx) - robot.direction.deg();
		
		double za[][] = {{dr}, {da}, {j}};
		Matrix z = new Matrix(za);
		
		Matrix dz = new Matrix(3, 1);
		float _dx = observation.position.x;
		float _dy = observation.position.y;
		float _q = _dx*_dx + _dy*_dy;
		double _dr = Math.sqrt(_q);
		double _da = Math.atan2(_dy, _dx) - robot.direction.deg();
		double _za[][] = {{_dr}, {_da}, {j}};
		Matrix _z = new Matrix(_za);
		dz = _z.minus(z);
		
		Matrix Fxj = new Matrix(6, 3*N + 3);
		Fxj.set(0, 0, 1);
		Fxj.set(1, 1, 1);
		Fxj.set(2, 2, 1);
		Fxj.set(3, 3*j, 1);
		Fxj.set(4, 3*j+1, 1);
		Fxj.set(5, 3*j+2, 1);
		
		double ht[][] = {
			{-dr*dx, -dr*dy, 0, dr*dx, dr*dy, 0},
			{dy, -dx, -q, -dy, dx, 0},
			{0, 0, 0, 0, 0, q}
		};
		Matrix Ht = new Matrix(ht).times(Fxj).times(1/q);
		Matrix Kt = sigma.times(Ht.transpose().times(
			Ht.times(sigma.times(Ht.transpose()).plus(Qt)).inverse())
		);
		mu = (AugmentedState)mu.plus(Kt.times(dz));
		sigma = (I.minus(Kt.times(Ht))).times(sigma);
	}
	
	public Pose getPose() {
		return mu.getRobotPose();
	}
	
	public Matrix getSigma() {
		return sigma;
	}
}