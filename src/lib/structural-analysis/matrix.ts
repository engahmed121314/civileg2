// ============================================================================
// MATRIX UTILITIES — Gaussian elimination solver
// ============================================================================

/**
 * Solve Ax = b using Gaussian elimination with partial pivoting.
 * A is n×n, b is n×1. Returns x as n×1 array.
 */
export function solveLinearSystem(A: number[][], b: number[]): number[] {
  const n = b.length;

  // Create augmented matrix [A|b]
  const aug: number[][] = A.map((row, i) => [...row, b[i]]);

  // Forward elimination with partial pivoting
  for (let col = 0; col < n; col++) {
    // Find pivot
    let maxVal = Math.abs(aug[col][col]);
    let maxRow = col;
    for (let row = col + 1; row < n; row++) {
      if (Math.abs(aug[row][col]) > maxVal) {
        maxVal = Math.abs(aug[row][col]);
        maxRow = row;
      }
    }

    // Swap rows
    if (maxRow !== col) {
      [aug[col], aug[maxRow]] = [aug[maxRow], aug[col]];
    }

    // Check for singularity
    const pivot = aug[col][col];
    if (Math.abs(pivot) < 1e-12) {
      throw new Error(`Singular matrix at column ${col}. Structure may be unstable or have mechanism.`);
    }

    // Eliminate below
    for (let row = col + 1; row < n; row++) {
      const factor = aug[row][col] / pivot;
      for (let j = col; j <= n; j++) {
        aug[row][j] -= factor * aug[col][j];
      }
    }
  }

  // Back substitution
  const x = new Array(n).fill(0);
  for (let i = n - 1; i >= 0; i--) {
    let sum = aug[i][n];
    for (let j = i + 1; j < n; j++) {
      sum -= aug[i][j] * x[j];
    }
    x[i] = sum / aug[i][i];
  }

  return x;
}

/**
 * Multiply two matrices A(m×n) × B(n×p) = C(m×p)
 */
export function matMul(A: number[][], B: number[][]): number[][] {
  const m = A.length;
  const n = A[0].length;
  const p = B[0].length;
  const C: number[][] = Array.from({ length: m }, () => new Array(p).fill(0));

  for (let i = 0; i < m; i++) {
    for (let j = 0; j < p; j++) {
      for (let k = 0; k < n; k++) {
        C[i][j] += A[i][k] * B[k][j];
      }
    }
  }
  return C;
}

/**
 * Multiply matrix A(m×n) by vector v(n×1) = result(m×1)
 */
export function matVecMul(A: number[][], v: number[]): number[] {
  const m = A.length;
  const n = v.length;
  const result = new Array(m).fill(0);
  for (let i = 0; i < m; i++) {
    for (let j = 0; j < n; j++) {
      result[i] += A[i][j] * v[j];
    }
  }
  return result;
}

/**
 * Transpose matrix A(m×n) = A^T(n×m)
 */
export function matTranspose(A: number[][]): number[][] {
  const m = A.length;
  const n = A[0].length;
  const T: number[][] = Array.from({ length: n }, () => new Array(m).fill(0));
  for (let i = 0; i < m; i++) {
    for (let j = 0; j < n; j++) {
      T[j][i] = A[i][j];
    }
  }
  return T;
}