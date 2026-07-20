package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lottery.entity.Fc3dModelRegistryEntity;
import com.lottery.mapper.Fc3dModelRegistryMapper;
import org.apache.ibatis.session.ResultHandler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sprint 10-E: trivial in-memory stand-in for {@link Fc3dModelRegistryMapper}, used ONLY in tests
 * to verify that {@code Fc3dModelRegistryService} truly persists state (no reliance on any
 * in-memory field of the service itself) — simulating a restart means constructing a brand-new
 * service instance pointed at the SAME fake "table".
 *
 * <p>{@code Fc3dModelRegistryService} only ever calls {@code insert}, {@code updateById},
 * {@code selectList(null)} and {@code selectCount(null)} — every lookup filters in Java after
 * fetching the (small) full table — so only those four need a real implementation here; every
 * other abstract method of {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} is unused.</p>
 */
public class Fc3dModelRegistryMapperFake implements Fc3dModelRegistryMapper {

    private final List<Fc3dModelRegistryEntity> rows = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public int insert(Fc3dModelRegistryEntity entity) {
        entity.setId(sequence.getAndIncrement());
        rows.add(entity);
        return 1;
    }

    @Override
    public int updateById(Fc3dModelRegistryEntity entity) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId().equals(entity.getId())) {
                rows.set(i, entity);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public List<Fc3dModelRegistryEntity> selectList(Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        return new ArrayList<>(rows);
    }

    @Override
    public Long selectCount(Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        return (long) rows.size();
    }

    private UnsupportedOperationException unused() {
        return new UnsupportedOperationException("not used by Fc3dModelRegistryService");
    }

    @Override
    public int deleteById(Serializable id) {
        throw unused();
    }

    @Override
    public int deleteById(Fc3dModelRegistryEntity entity) {
        throw unused();
    }

    @Override
    public int delete(Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public int deleteBatchIds(Collection<?> idList) {
        throw unused();
    }

    @Override
    public int update(Fc3dModelRegistryEntity entity, Wrapper<Fc3dModelRegistryEntity> updateWrapper) {
        throw unused();
    }

    @Override
    public Fc3dModelRegistryEntity selectById(Serializable id) {
        throw unused();
    }

    @Override
    public List<Fc3dModelRegistryEntity> selectBatchIds(Collection<? extends Serializable> idList) {
        throw unused();
    }

    @Override
    public void selectBatchIds(Collection<? extends Serializable> idList, ResultHandler<Fc3dModelRegistryEntity> resultHandler) {
        throw unused();
    }

    @Override
    public void selectList(Wrapper<Fc3dModelRegistryEntity> queryWrapper, ResultHandler<Fc3dModelRegistryEntity> resultHandler) {
        throw unused();
    }

    @Override
    public List<Fc3dModelRegistryEntity> selectList(IPage<Fc3dModelRegistryEntity> page, Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectList(IPage<Fc3dModelRegistryEntity> page, Wrapper<Fc3dModelRegistryEntity> queryWrapper,
                           ResultHandler<Fc3dModelRegistryEntity> resultHandler) {
        throw unused();
    }

    @Override
    public List<Map<String, Object>> selectMaps(Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectMaps(Wrapper<Fc3dModelRegistryEntity> queryWrapper, ResultHandler<Map<String, Object>> resultHandler) {
        throw unused();
    }

    @Override
    public List<Map<String, Object>> selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public void selectMaps(IPage<? extends Map<String, Object>> page, Wrapper<Fc3dModelRegistryEntity> queryWrapper,
                           ResultHandler<Map<String, Object>> resultHandler) {
        throw unused();
    }

    @Override
    public <E> List<E> selectObjs(Wrapper<Fc3dModelRegistryEntity> queryWrapper) {
        throw unused();
    }

    @Override
    public <E> void selectObjs(Wrapper<Fc3dModelRegistryEntity> queryWrapper, ResultHandler<E> resultHandler) {
        throw unused();
    }
}
