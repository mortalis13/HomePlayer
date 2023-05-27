#include "AudioFilter.h"

double PeakingFilter::processAudioSample(double xn, uint8_t ch) {
  // yn <= a0 xn + a1 xz1 + a2 xz2 - b1 yz1 - b2 yz2
  double yn = cf_a0 * xn + cf_a1 * states[ch][0] + cf_a2 * states[ch][1] - cf_b1 * states[ch][2] - cf_b2 * states[ch][3];
  checkFloatUnderflow(yn);
  
  // xz2 <= xz1 <= xn ; yz2 <= yz1 <= yn
  states[ch][1] = states[ch][0];
  states[ch][0] = xn;
  states[ch][3] = states[ch][2];
  states[ch][2] = yn;

  return xn + cf_c0 * yn;
}

void PeakingFilter::calculateFilterCoeffs() {
  // Non constant Q
  double theta_c = 2.0 * kPi * fc / sampleRate;
  double mu = pow(10.0, db / 20.0);

  double tanArg = theta_c / (2.0 * Q);
  if (tanArg >= 0.95 * kPi / 2.0) tanArg = 0.95 * kPi / 2.0;

  double zeta = 4.0 / (1.0 + mu);
  double betaNumerator = 1.0 - zeta * tan(tanArg);
  double betaDenominator = 1.0 + zeta * tan(tanArg);

  double beta = 0.5 * (betaNumerator / betaDenominator);
  double gamma = (0.5 + beta) * (cos(theta_c));
  double alpha = (0.5 - beta);

  cf_a0 = alpha;
  cf_a1 = 0.0;
  cf_a2 = -alpha;
  cf_b1 = -2.0 * gamma;
  cf_b2 = 2.0 * beta;

  cf_c0 = mu - 1.0;
}
