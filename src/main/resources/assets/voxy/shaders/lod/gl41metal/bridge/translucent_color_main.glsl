
      void main() {
        vec2 targetPixel = gl_FragCoord.xy;
        vec2 sharedPixel = sharedPixelForTarget(targetPixel);
        VoxyTranslucentTexel g = sampleVoxyTranslucent(sharedPixel);
        if (g.coverage <= 0.0 || g.depth <= 0.0 || g.depth >= 1.0 || g.alpha <= 0.0) {
          discard;
        }
__VOXY_TRANSLUCENT_BOUND__        voxyQuadFlags = g.flags;
        voxy_OverrideFragCoord = vec4(gl_FragCoord.xy, g.depth, gl_FragCoord.w);
        VoxyFragmentParameters parameters =
            VoxyFragmentParameters(
                vec4(g.colour.rgb, g.alpha), g.tile, g.uv, int(g.face), g.modelId, g.lightMap,
                g.tint, g.customId);
        voxy_emitFragment(parameters);
__VOXY_TRANSLUCENT_TAIL__      }
