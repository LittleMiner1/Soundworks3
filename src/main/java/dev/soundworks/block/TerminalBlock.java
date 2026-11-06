package dev.soundworks.block;

import dev.soundworks.Hooks;
import dev.soundworks.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Terminal on a wall (clicked side face) or on a table (clicked top face). facing = direction the screen faces. */
public class TerminalBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WALL = BooleanProperty.create("wall");

	public TerminalBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING, WALL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Direction face = ctx.getClickedFace();
		BlockState state;
		if (face.getAxis().isHorizontal()) {
			state = defaultBlockState().setValue(WALL, true).setValue(FACING, face);
		} else if (face == Direction.UP) {
			state = defaultBlockState().setValue(WALL, false).setValue(FACING, ctx.getHorizontalDirection().getOpposite());
		} else {
			return null; // not on ceilings
		}
		return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(WALL)) {
			Direction facing = state.getValue(FACING);
			BlockPos support = pos.relative(facing.getOpposite());
			return level.getBlockState(support).isFaceSturdy(level, support, facing);
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		Direction f = state.getValue(FACING);
		return state.getValue(WALL)
			? BlockUtil.rotated(f, 1, 3, 13, 15, 13, 16)
			: BlockUtil.rotated(f, 1, 0, 5, 15, 13, 11);
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) Hooks.openNet.accept(pos, false);
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TerminalBlockEntity(pos, state);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModRegistry.TERMINAL_BE, TerminalBlockEntity::tick);
	}
}
