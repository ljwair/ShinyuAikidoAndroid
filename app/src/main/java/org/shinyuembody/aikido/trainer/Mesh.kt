package org.shinyuembody.aikido.trainer

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Mesh(
    vertices: FloatArray,
    normals: FloatArray,
    indices: ShortArray
) {
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(vertices)
            position(0)
        }

    private val normalBuffer: FloatBuffer = ByteBuffer.allocateDirect(normals.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(normals)
            position(0)
        }

    private val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(indices)
            position(0)
        }

    private val indexCount = indices.size

    fun draw(positionHandle: Int, normalHandle: Int) {
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        normalBuffer.position(0)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        GLES20.glEnableVertexAttribArray(normalHandle)

        indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
    }

    companion object {
        fun cube(): Mesh {
            val v = floatArrayOf(
                // front
                -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f,0.5f,0.5f,  -0.5f,0.5f,0.5f,
                // back
                 0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f,  0.5f,0.5f,-0.5f,
                // left
                -0.5f,-0.5f,-0.5f, -0.5f,-0.5f, 0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
                // right
                 0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
                // top
                -0.5f,0.5f, 0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
                // bottom
                -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
            )
            val n = floatArrayOf(
                0f,0f,1f, 0f,0f,1f, 0f,0f,1f, 0f,0f,1f,
                0f,0f,-1f, 0f,0f,-1f, 0f,0f,-1f, 0f,0f,-1f,
                -1f,0f,0f, -1f,0f,0f, -1f,0f,0f, -1f,0f,0f,
                1f,0f,0f, 1f,0f,0f, 1f,0f,0f, 1f,0f,0f,
                0f,1f,0f, 0f,1f,0f, 0f,1f,0f, 0f,1f,0f,
                0f,-1f,0f, 0f,-1f,0f, 0f,-1f,0f, 0f,-1f,0f
            )
            val idx = ShortArray(36)
            var k = 0
            for (face in 0 until 6) {
                val o = (face * 4).toShort()
                idx[k++] = o
                idx[k++] = (o + 1).toShort()
                idx[k++] = (o + 2).toShort()
                idx[k++] = o
                idx[k++] = (o + 2).toShort()
                idx[k++] = (o + 3).toShort()
            }
            return Mesh(v, n, idx)
        }

        fun sphere(latSegments: Int = 18, lonSegments: Int = 24): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val inds = ArrayList<Short>()

            for (lat in 0..latSegments) {
                val theta = PI * lat / latSegments
                val sy = cos(theta).toFloat()
                val ring = sin(theta).toFloat()
                for (lon in 0..lonSegments) {
                    val phi = 2.0 * PI * lon / lonSegments
                    val x = (ring * cos(phi)).toFloat()
                    val z = (ring * sin(phi)).toFloat()
                    verts.add(x); verts.add(sy); verts.add(z)
                    norms.add(x); norms.add(sy); norms.add(z)
                }
            }

            val stride = lonSegments + 1
            for (lat in 0 until latSegments) {
                for (lon in 0 until lonSegments) {
                    val a = (lat * stride + lon).toShort()
                    val b = ((lat + 1) * stride + lon).toShort()
                    val c = ((lat + 1) * stride + lon + 1).toShort()
                    val d = (lat * stride + lon + 1).toShort()
                    inds.add(a); inds.add(b); inds.add(c)
                    inds.add(a); inds.add(c); inds.add(d)
                }
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), inds.toShortArray())
        }

        fun frustum(topX: Float = 0.36f, bottomX: Float = 0.58f, depth: Float = 0.42f): Mesh {
            val ty = 0.5f
            val by = -0.5f
            val tz = depth / 2f
            val bx = bottomX / 2f
            val tx = topX / 2f
            val points = arrayOf(
                floatArrayOf(-tx,ty,tz), floatArrayOf(tx,ty,tz), floatArrayOf(tx,ty,-tz), floatArrayOf(-tx,ty,-tz),
                floatArrayOf(-bx,by,tz), floatArrayOf(bx,by,tz), floatArrayOf(bx,by,-tz), floatArrayOf(-bx,by,-tz)
            )
            val faces = arrayOf(
                intArrayOf(0,1,5,4), intArrayOf(2,3,7,6), intArrayOf(3,0,4,7),
                intArrayOf(1,2,6,5), intArrayOf(3,2,1,0), intArrayOf(4,5,6,7)
            )
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()
            var base: Short = 0
            for (f in faces) {
                val p0 = points[f[0]]; val p1 = points[f[1]]; val p2 = points[f[2]]
                val ux = p1[0]-p0[0]; val uy = p1[1]-p0[1]; val uz = p1[2]-p0[2]
                val vx = p2[0]-p0[0]; val vy = p2[1]-p0[1]; val vz = p2[2]-p0[2]
                var nx = uy*vz-uz*vy; var ny = uz*vx-ux*vz; var nz = ux*vy-uy*vx
                val len = kotlin.math.sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(0.0001f)
                nx/=len; ny/=len; nz/=len
                for (pi in f) {
                    val p = points[pi]
                    verts.add(p[0]); verts.add(p[1]); verts.add(p[2])
                    norms.add(nx); norms.add(ny); norms.add(nz)
                }
                idx.add(base); idx.add((base+1).toShort()); idx.add((base+2).toShort())
                idx.add(base); idx.add((base+2).toShort()); idx.add((base+3).toShort())
                base = (base + 4).toShort()
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }
    }
}
