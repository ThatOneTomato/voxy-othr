
      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyGbufferTexel g = sampleVoxyGbuffer(sharedPixel);
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0) {
          discard;
        }
__VOXY_OPAQUE_MASK__        voxyQuadFlags = g.flags;
        voxy_OverrideFragCoord = vec4(gl_FragCoord.xy, g.depth, gl_FragCoord.w);
        // parameters.tile/uv keep the GL46 split: tile = quad tile (uvTile.zw), uv = atlas uv.
        VoxyFragmentParameters parameters =
            VoxyFragmentParameters(
                g.colour, g.tile, g.uv, int(g.face), g.modelId, g.lightMap, g.tint, g.customId);
        voxy_emitFragment(parameters);
        gl_FragDepth = __VOXY_OPAQUE_FRAGDEPTH__;
      }
